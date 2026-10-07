package mk.ukim.finki.aibotbackend.bot.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import mk.ukim.finki.aibotbackend.bot.browser.PageSnapshot;
import mk.ukim.finki.aibotbackend.model.enums.BotActionType;
import mk.ukim.finki.aibotbackend.model.exception.BotExecutionException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

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
    private static final Pattern RETRY_DELAY = Pattern.compile("\"retryDelay\"\\s*:\\s*\"(\\d+)");

    private static final String DECISION_PROMPT = """
        You control a web browser for a bot that collects public Macedonian-language posts from a social network \
        and donates them to doniraj.vezilka.ai, a platform that preserves the Macedonian language.

        On every step you get the goal, the actions done so far and the current page as simplified HTML. \
        Its first line tells how far the page is scrolled. The elements you can use have a data-bot-id number.

        Reply with the single next action:
        - NAVIGATE: open a page, target = the full URL
        - CLICK: click an element, target = its data-bot-id number
        - TYPE: type into an input, target = its data-bot-id number, value = the text (end it with \\n to press Enter)
        - SCROLL: scroll down to load more content
        - WAIT: wait for the page to finish loading
        - EXTRACT: collect the posts that are visible right now
        - LOGIN: log in, only when the content is not shown without it
        - FINISH: stop, when the goal is done or cannot be done

        Rules:
        - Use only data-bot-id numbers from the current page.
        - EXTRACT when posts with Macedonian text are visible, then SCROLL to load new ones. \
        Do not extract the same view twice in a row.
        - Close cookie and consent dialogs by rejecting the optional cookies.
        - Only read public content. Never post, like, comment, follow, subscribe or send messages.
        - If an action did not change anything, do not repeat it. Try something else or FINISH.
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
        return generate(systemPrompt, userPrompt, null, null);
    }

    @Override
    public BotDecision decideNextAction(PageSnapshot snapshot, String goal, List<BotAction> history) {
        String answer = generate(DECISION_PROMPT, describe(snapshot, goal, history), snapshot.screenshotBase64(), DECISION_SCHEMA);
        BotDecision decision = parseDecision(answer);
        log.debug("Decided {} {} ({})", decision.action().type(), decision.action().target(), decision.rationale());
        return decision;
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
            .append("\nCURRENT PAGE\n")
            .append("URL: ").append(snapshot.url()).append('\n')
            .append("TITLE: ").append(snapshot.title()).append('\n')
            .append(snapshot.domContent())
            .toString();
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
            return new BotDecision(action, json.path("goalReached").asBoolean(false), reasoning);
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
