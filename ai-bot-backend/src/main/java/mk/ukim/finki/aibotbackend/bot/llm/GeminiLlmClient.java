package mk.ukim.finki.aibotbackend.bot.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import mk.ukim.finki.aibotbackend.bot.browser.PageSnapshot;
import mk.ukim.finki.aibotbackend.model.enums.BotActionType;
import mk.ukim.finki.aibotbackend.model.exception.BotExecutionException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Comment;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

// TODO(student): Replace this bean with an implementation backed by a real LLM.
/**
 * {@link LlmClient} backed by the Gemini API (Google AI Studio).
 *
 * <p>Decisions are requested as JSON that follows a fixed schema, so the answer of the model
 * can always be parsed into a {@link BotDecision}. Rate limits (HTTP 429) and server errors
 * are retried a few times before giving up.</p>
 */
@Slf4j
@Component
public class GeminiLlmClient implements LlmClient {
    private static final int HISTORY_SIZE = 15;
    private static final int MAX_ATTEMPTS = 3;
    private static final int MAX_PAGE_LENGTH = 24_000;
    private static final int MAX_LABEL_LENGTH = 100;
    private static final int MIN_SCROLLS_BEFORE_EXTRACT = 2;
    private static final Set<String> INLINE_TAGS = Set.of(
        "a", "span", "b", "i", "em", "strong", "small", "label", "time", "button", "abbr", "code"
    );
    private static final Pattern RETRY_DELAY = Pattern.compile("\"retryDelay\"\\s*:\\s*\"(\\d+)");
    // a goal can ask for a number of pages with this phrase, finishing before that number is reached is pointed out to the model
    private static final Pattern REQUIRED_PAGES = Pattern.compile("PAGES WITH EXTRACTED POSTS shows (\\d+)");
    private static final String EARLY_FINISH_NOTE = """

        NOTE: do not FINISH yet. The goal needs %d pages with extracted posts and there are only %d. \
        Go on with the next page, FINISH only if there are no more pages to open.""";
    private static final String NOT_DONE_YET = "Not done yet: ";
    private static final int MAX_TIMES_SENT_BACK = 2;
    private static final String REPEATED_EXTRACT_NOTE = """

        NOTE: the last action was EXTRACT and the page has not changed since, so its posts are already collected. \
        Do not EXTRACT again, choose the next step of the goal.""";

    private static final String DECISION_PROMPT = """
        You control a web browser for a bot that collects public Macedonian-language posts from a social network \
        and donates them to doniraj.vezilka.ai, a platform that preserves the Macedonian language.

        On every step you get the goal, the last actions, the list of all pages opened so far and the text \
        of the current page. Its first line tells how far the page is scrolled. The elements you can use are \
        written as [number tag "label" link].

        Reply with the single next action:
        - NAVIGATE: open a page, target = the full URL
        - CLICK: click an element, target = its number
        - TYPE: type into an input, target = its number, value = the text (end it with \\n to press Enter)
        - SCROLL: scroll down to load more content
        - WAIT: wait for the page to finish loading
        - EXTRACT: collect the posts that are visible right now
        - LOGIN: log in, only when the content is not shown without it
        - FINISH: stop, when the goal is done or cannot be done

        Rules:
        - Use only element numbers from the current page.
        - When the goal gives exact steps for a page, follow them. Otherwise EXTRACT when posts with \
        Macedonian text are visible, then SCROLL to load new ones.
        - Do not extract the same view twice in a row.
        - Close cookie and consent dialogs by rejecting the optional cookies.
        - Only read public content. Never post, like, comment, follow, subscribe or send messages.
        - If an action did not change anything, do not repeat it. Try something else or FINISH.
        - The number in the line PAGES WITH EXTRACTED POSTS and the pages listed under it are exact. \
        Read numbers from there and do not count from memory. Do not open a page from that list again.
        - Set goalReached to true only when the goal is done.
        Write the reasoning as one short English sentence.
        """;

    private static final Map<String, Object> DECISION_SCHEMA = Map.of(
        "type", "OBJECT",
        "properties", Map.of(
            "action", Map.of("type", "STRING", "enum", Arrays.stream(BotActionType.values()).map(Enum::name).toList()),
            "target", Map.of("type", "STRING", "nullable", true),
            "value", Map.of("type", "STRING", "nullable", true),
            "reasoning", Map.of("type", "STRING"),
            "goalReached", Map.of("type", "BOOLEAN")
        ),
        "required", List.of("action", "reasoning", "goalReached")
    );

    private final RestClient restClient;
    private final GeminiProperties properties;
    private final ObjectMapper objectMapper;

    public GeminiLlmClient(RestClient.Builder restClientBuilder, GeminiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder
            .baseUrl(properties.baseUrl())
            .defaultHeader("x-goog-api-key", properties.apiKey())
            .build();
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        // TODO(student): Implement LlmClient.complete().
        return generate(systemPrompt, userPrompt, null, null);
    }

    @Override
    public BotDecision decideNextAction(PageSnapshot snapshot, String goal, List<BotAction> history) {
        // TODO(student): Implement LlmClient.decideNextAction().
        String prompt = describe(snapshot, goal, history);
        BotAction lastAction = history.isEmpty() ? null : history.getLast();
        Map<String, PageProgress> pages = pagesOf(history);
        long extractedPages = pages.values().stream().filter(page -> page.extracted).count();
        Matcher requiredMatcher = REQUIRED_PAGES.matcher(goal);
        int requiredPages = requiredMatcher.find() ? Integer.parseInt(requiredMatcher.group(1)) : 0;
        boolean tooEarly = extractedPages < requiredPages;
        String earlyFinishNote = EARLY_FINISH_NOTE.formatted(requiredPages, extractedPages);

        BotDecision decision = ask(prompt, snapshot);
        // the model miscounts the pages it has done and finishes too early, so it is told the real numbers and asked again
        if (finishes(decision) && tooEarly) {
            decision = ask(prompt + earlyFinishNote, snapshot);
        }
        // the model sometimes extracts the same unchanged page again and again, so it is asked once more with that pointed out
        if (decision.action().type() == BotActionType.EXTRACT && lastAction != null && lastAction.type() == BotActionType.EXTRACT) {
            decision = ask(prompt + REPEATED_EXTRACT_NOTE + (tooEarly ? earlyFinishNote : ""), snapshot);
        }

        String startPage = startPage(history);
        String currentPage = currentPage(history);
        BotActionType type = decision.action().type();
        boolean leaves = finishes(decision) || type == BotActionType.NAVIGATE;
        int scrolls = scrollsOnThisPage(history);
        if (leaves && lastAction != null && lastAction.type() == BotActionType.SCROLL) {
            // the model sometimes leaves a page right after scrolling, the newly loaded posts would then never be extracted
            decision = scrolls < MIN_SCROLLS_BEFORE_EXTRACT
                ? replaced(decision, BotActionType.SCROLL, "Scroll once more before leaving the page, so that more posts are loaded.")
                : replaced(decision, BotActionType.EXTRACT, "Extract what the last scroll loaded before leaving the page.");
        } else if (finishes(decision) && tooEarly && startPage != null && timesSentBack(history) < MAX_TIMES_SENT_BACK) {
            // when it still insists on finishing too early, it is taken back to the page the run started on, where the next
            // page can be chosen; a few times at most, in case there really are no more pages to open
            String reasoning = NOT_DONE_YET + "the goal needs " + requiredPages + " pages with extracted posts and there are "
                + extractedPages + ", back to the start page.";
            decision = navigateTo(decision, startPage, reasoning);
        } else if (type == BotActionType.NAVIGATE && startPage != null && currentPage != null && !currentPage.equals(startPage)
            && pages.get(currentPage).extracted && !startPage.equals(target(decision))) {
            // after a page is done the next one is chosen on the start page, links on the page itself can lead elsewhere,
            // like the suggested videos of other channels next to a video
            decision = navigateTo(decision, startPage, "Back to the start page to choose the next page from its list.");
        } else if (!leaves && type == BotActionType.EXTRACT && scrolls < MIN_SCROLLS_BEFORE_EXTRACT) {
            // posts further down a page (like comments) are loaded only when it is scrolled, so that comes before extracting
            decision = replaced(decision, BotActionType.SCROLL, "Scroll twice before extracting, so that more posts are loaded.");
        }
        log.debug("Decided {} {} ({})", decision.action().type(), decision.action().target(), decision.rationale());
        return decision;
    }

    private BotDecision ask(String prompt, PageSnapshot snapshot) {
        return parseDecision(generate(DECISION_PROMPT, prompt, snapshot.screenshotBase64(), DECISION_SCHEMA));
    }

    private static boolean finishes(BotDecision decision) {
        return decision.goalReached() || decision.action().type() == BotActionType.FINISH;
    }

    private static BotDecision replaced(BotDecision decision, BotActionType type, String reasoning) {
        return new BotDecision(new BotAction(type, null, null, reasoning), false, decision.rationale());
    }

    private static BotDecision navigateTo(BotDecision decision, String url, String reasoning) {
        return new BotDecision(new BotAction(BotActionType.NAVIGATE, url, null, reasoning), false, decision.rationale());
    }

    private static String target(BotDecision decision) {
        return decision.action().target() == null ? null : decision.action().target().strip();
    }

    private static long timesSentBack(List<BotAction> history) {
        return history.stream()
            .filter(action -> action.reasoning() != null && action.reasoning().startsWith(NOT_DONE_YET))
            .count();
    }

    private static String startPage(List<BotAction> history) {
        return history.stream()
            .filter(action -> action.type() == BotActionType.NAVIGATE && action.target() != null)
            .map(action -> action.target().strip())
            .findFirst()
            .orElse(null);
    }

    private static String currentPage(List<BotAction> history) {
        return history.reversed().stream()
            .filter(action -> action.type() == BotActionType.NAVIGATE && action.target() != null)
            .map(action -> action.target().strip())
            .findFirst()
            .orElse(null);
    }

    private static int scrollsOnThisPage(List<BotAction> history) {
        int scrolls = 0;
        for (BotAction action : history.reversed()) {
            if (action.type() == BotActionType.NAVIGATE) {
                break;
            }
            if (action.type() == BotActionType.SCROLL) {
                scrolls++;
            }
        }
        return scrolls;
    }

    private String describe(PageSnapshot snapshot, String goal, List<BotAction> history) {
        StringBuilder prompt = new StringBuilder()
            .append("GOAL: ").append(goal).append("\n\n")
            .append("ACTIONS SO FAR:\n");
        if (history.isEmpty()) {
            prompt.append("none, this is the first step\n");
        }
        for (int i = Math.max(0, history.size() - HISTORY_SIZE); i < history.size(); i++) {
            BotAction action = history.get(i);
            prompt.append(i + 1).append(". ").append(action.type());
            if (action.target() != null) {
                prompt.append(' ').append(action.target());
            }
            if (action.value() != null) {
                prompt.append(" \"").append(action.value().strip()).append('"');
            }
            if (action.reasoning() != null) {
                prompt.append(" (").append(action.reasoning()).append(')');
            }
            prompt.append('\n');
        }
        return prompt
            .append('\n').append(describePages(history))
            .append("\nCURRENT PAGE\n")
            .append("URL: ").append(snapshot.url()).append('\n')
            .append("TITLE: ").append(snapshot.title()).append('\n')
            .append(textOf(snapshot.domContent()))
            .toString();
    }

    /**
     * The model sees only the last actions and it miscounts them, so every page it opened with NAVIGATE
     * is listed for it: first the numbered pages whose posts are already extracted, then the other ones.
     */
    private static String describePages(List<BotAction> history) {
        Map<String, PageProgress> pages = pagesOf(history);
        long extractedPages = pages.values().stream().filter(page -> page.extracted).count();

        StringBuilder done = new StringBuilder("PAGES WITH EXTRACTED POSTS: ").append(extractedPages).append('\n');
        StringBuilder other = new StringBuilder("OTHER OPENED PAGES:\n");
        int number = 1;
        for (Map.Entry<String, PageProgress> entry : pages.entrySet()) {
            PageProgress page = entry.getValue();
            String line = entry.getKey() + " (scrolls: " + page.scrolls + ", " + page.describeExtraction() + ")\n";
            if (page.extracted) {
                done.append(number++).append(". ").append(line);
            } else {
                other.append("- ").append(line);
            }
        }
        return done.append(other).toString();
    }

    private static Map<String, PageProgress> pagesOf(List<BotAction> history) {
        Map<String, PageProgress> pages = new LinkedHashMap<>();
        PageProgress currentPage = null;
        for (BotAction action : history) {
            if (action.type() == BotActionType.NAVIGATE && action.target() != null) {
                currentPage = pages.computeIfAbsent(action.target().strip(), url -> new PageProgress());
            } else if (currentPage != null && action.type() == BotActionType.SCROLL) {
                currentPage.scrolls++;
                currentPage.scrolledAfterExtract = true;
            } else if (currentPage != null && action.type() == BotActionType.EXTRACT) {
                currentPage.extracted = true;
                currentPage.scrolledAfterExtract = false;
            }
        }
        return pages;
    }

    private static final class PageProgress {
        private int scrolls;
        private boolean extracted;
        private boolean scrolledAfterExtract;

        private String describeExtraction() {
            if (!extracted) {
                return "nothing extracted";
            }
            return scrolledAfterExtract ? "the last scroll is not extracted yet" : "posts extracted";
        }
    }

    /**
     * The snapshot keeps the whole page for the content extractor, the model gets a much shorter text view of it:
     * plain text, plus the elements it can use written as [number tag "label" link].
     */
    private static String textOf(String domContent) {
        if (domContent == null) {
            return "";
        }
        StringBuilder view = new StringBuilder();
        Jsoup.parseBodyFragment(domContent).body().childNodes().forEach(node -> appendNode(node, view));
        String text = view.toString()
            .replaceAll("[ \\t]+", " ")
            .replaceAll(" *\\n[ \\n]*", "\n")
            .strip();
        return text.length() <= MAX_PAGE_LENGTH
            ? text
            : text.substring(0, MAX_PAGE_LENGTH) + "\n(the rest of the page is cut off)";
    }

    private static void appendNode(Node node, StringBuilder view) {
        switch (node) {
            case TextNode text -> view.append(text.text());
            case Comment comment -> view.append('(').append(comment.getData().strip()).append(")\n");
            case Element element -> appendElement(element, view);
            default -> {
            }
        }
    }

    private static void appendElement(Element element, StringBuilder view) {
        if (element.hasAttr("data-bot-id")) {
            view.append(" [").append(element.attr("data-bot-id")).append(' ').append(element.tagName());
            String label = labelOf(element);
            if (!label.isEmpty()) {
                view.append(" \"").append(label).append('"');
            }
            if (element.hasAttr("href")) {
                view.append(' ').append(element.attr("href"));
            }
            view.append("] ");
            return;
        }
        if (element.tagName().equals("img")) {
            return;
        }
        boolean block = !INLINE_TAGS.contains(element.tagName());
        if (block) {
            view.append('\n');
        }
        element.childNodes().forEach(child -> appendNode(child, view));
        if (block) {
            view.append('\n');
        }
    }

    private static String labelOf(Element element) {
        String label = element.text();
        if (label.isBlank()) {
            label = Stream.of("aria-label", "placeholder", "title", "value", "alt")
                .map(element::attr)
                .filter(attribute -> !attribute.isBlank())
                .findFirst()
                .orElse("");
        }
        label = label.replaceAll("\\s+", " ").strip();
        return label.length() <= MAX_LABEL_LENGTH ? label : label.substring(0, MAX_LABEL_LENGTH) + "...";
    }

    private String generate(String systemPrompt, String userPrompt, String screenshotBase64, Map<String, Object> responseSchema) {
        List<Map<String, Object>> parts = new ArrayList<>();
        parts.add(Map.of("text", userPrompt));
        if (screenshotBase64 != null) {
            parts.add(Map.of("inline_data", Map.of("mime_type", "image/png", "data", screenshotBase64)));
        }

        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("temperature", 0.2);
        if (responseSchema != null) {
            generationConfig.put("responseMimeType", "application/json");
            generationConfig.put("responseSchema", responseSchema);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt))));
        }
        body.put("contents", List.of(Map.of("role", "user", "parts", parts)));
        body.put("generationConfig", generationConfig);

        for (int attempt = 1; ; attempt++) {
            try {
                JsonNode response = restClient.post()
                    .uri("/models/{model}:generateContent", properties.model())
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
                return textOf(response);
            } catch (HttpClientErrorException.TooManyRequests | HttpServerErrorException e) {
                if (attempt == MAX_ATTEMPTS) {
                    throw new BotExecutionException("Gemini is not available right now (%s).".formatted(e.getStatusCode()), e);
                }
                long seconds = retryDelaySeconds(e, attempt);
                log.warn("Gemini answered {}, trying again in {} s.", e.getStatusCode(), seconds);
                pause(seconds * 1000);
            } catch (RestClientException e) {
                throw new BotExecutionException("Calling Gemini failed: " + e.getMessage(), e);
            }
        }
    }

    private String textOf(JsonNode response) {
        JsonNode candidate = response == null ? null : response.path("candidates").path(0);
        JsonNode parts = candidate == null ? null : candidate.path("content").path("parts");
        if (parts == null || !parts.isArray() || parts.isEmpty()) {
            String reason = candidate == null
                ? "empty response"
                : candidate.path("finishReason").asText(response.path("promptFeedback").path("blockReason").asText("no content"));
            throw new BotExecutionException("Gemini returned no answer (%s).".formatted(reason));
        }

        StringBuilder text = new StringBuilder();
        for (JsonNode part : parts) {
            // thinking models can send their thoughts as separate parts, those are not the answer
            if (!part.path("thought").asBoolean(false)) {
                text.append(part.path("text").asText(""));
            }
        }
        return text.toString().strip();
    }

    private BotDecision parseDecision(String answer) {
        try {
            JsonNode json = objectMapper.readTree(answer);
            BotActionType type = BotActionType.valueOf(json.path("action").asText().strip().toUpperCase());
            String reasoning = textOrNull(json.path("reasoning"));
            BotAction action = new BotAction(type, textOrNull(json.path("target")), textOrNull(json.path("value")), reasoning);
            // the goal counts as reached only together with FINISH, the model sometimes marks other actions as done by mistake
            boolean goalReached = type == BotActionType.FINISH && json.path("goalReached").asBoolean(false);
            return new BotDecision(action, goalReached, reasoning);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new BotExecutionException("Gemini returned an invalid decision: " + answer, e);
        }
    }

    private static String textOrNull(JsonNode node) {
        return node.isValueNode() && !node.isNull() && !node.asText().isBlank() ? node.asText() : null;
    }

    private static long retryDelaySeconds(RestClientResponseException e, int attempt) {
        Matcher matcher = RETRY_DELAY.matcher(e.getResponseBodyAsString());
        return matcher.find() ? Math.min(Long.parseLong(matcher.group(1)), 60) : attempt * 5L;
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
