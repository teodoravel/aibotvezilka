package mk.ukim.finki.aibotbackend.bot.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import mk.ukim.finki.aibotbackend.bot.browser.PageSnapshot;
import mk.ukim.finki.aibotbackend.model.enums.BotActionType;
import mk.ukim.finki.aibotbackend.model.exception.BotExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Tests the Gemini client against a mocked API, so no key or network is needed.
 */
public class GeminiLlmClientTest {
    private static final String URL = "https://gemini.test/v1beta/models/test-model:generateContent";
    private static final PageSnapshot SNAPSHOT = new PageSnapshot(
        "https://www.youtube.com/@test",
        "Тест канал",
        "<a data-bot-id=\"3\" href=\"/watch?v=1\">Вести од Скопје</a>",
        null
    );
    private static final String GOAL_OF_THREE_PAGES = "Collect posts. FINISH only when the line PAGES WITH EXTRACTED POSTS shows 3.";
    private static final String START_PAGE = "https://www.youtube.com/@test/videos";
    private static final List<BotAction> ONE_EXTRACTED_PAGE = List.of(
        new BotAction(BotActionType.NAVIGATE, START_PAGE, null, "Open the channel."),
        new BotAction(BotActionType.NAVIGATE, "https://www.youtube.com/watch?v=1", null, "Open the video."),
        new BotAction(BotActionType.SCROLL, null, null, "Load the comments."),
        new BotAction(BotActionType.SCROLL, null, null, "Load more comments."),
        new BotAction(BotActionType.EXTRACT, null, null, "Comments are visible.")
    );

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockRestServiceServer server;
    private GeminiLlmClient llmClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        GeminiProperties properties = new GeminiProperties("https://gemini.test/v1beta", "test-key", "test-model");
        llmClient = new GeminiLlmClient(builder, properties, objectMapper);
    }

    @Test
    void testDecideNextActionParsesTheDecision() {
        server.expect(requestTo(URL))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("x-goog-api-key", "test-key"))
            .andExpect(content().string(containsString("GOAL: Отвори го видеото")))
            .andRespond(withSuccess(answer("""
                {"action": "CLICK", "target": "3", "value": null, "reasoning": "Open the video.", "goalReached": false}
                """), MediaType.APPLICATION_JSON));

        BotDecision decision = llmClient.decideNextAction(SNAPSHOT, "Отвори го видеото", List.of());

        assertThat(decision.goalReached()).isFalse();
        assertThat(decision.action().type()).isEqualTo(BotActionType.CLICK);
        assertThat(decision.action().target()).isEqualTo("3");
        assertThat(decision.action().value()).isNull();
        assertThat(decision.rationale()).isEqualTo("Open the video.");
        server.verify();
    }

    @Test
    void testHistoryIsSentToTheModel() {
        server.expect(requestTo(URL))
            .andExpect(content().string(containsString("1. NAVIGATE https://www.youtube.com/@test (Open the channel.)")))
            .andRespond(withSuccess(answer("""
                {"action": "EXTRACT", "reasoning": "Posts are visible.", "goalReached": false}
                """), MediaType.APPLICATION_JSON));

        List<BotAction> history = List.of(
            new BotAction(BotActionType.NAVIGATE, "https://www.youtube.com/@test", null, "Open the channel."),
            new BotAction(BotActionType.SCROLL, null, null, "Load the posts."),
            new BotAction(BotActionType.SCROLL, null, null, "Load more posts.")
        );
        BotDecision decision = llmClient.decideNextAction(SNAPSHOT, "Собери објави", history);

        assertThat(decision.action().type()).isEqualTo(BotActionType.EXTRACT);
        assertThat(decision.action().target()).isNull();
        server.verify();
    }

    @Test
    void testOpenedPagesAreListedForTheModel() {
        server.expect(requestTo(URL))
            .andExpect(content().string(containsString("PAGES WITH EXTRACTED POSTS: 1")))
            .andExpect(content().string(containsString("1. https://www.youtube.com/watch?v=1 (scrolls: 1, posts extracted)")))
            .andExpect(content().string(containsString("- https://www.youtube.com/@test (scrolls: 0, nothing extracted)")))
            .andRespond(withSuccess(answer("""
                {"action": "NAVIGATE", "target": "https://www.youtube.com/@test", "reasoning": "Next video.", "goalReached": false}
                """), MediaType.APPLICATION_JSON));

        List<BotAction> history = List.of(
            new BotAction(BotActionType.NAVIGATE, "https://www.youtube.com/@test", null, "Open the channel."),
            new BotAction(BotActionType.NAVIGATE, "https://www.youtube.com/watch?v=1", null, "Open the first video."),
            new BotAction(BotActionType.EXTRACT, null, null, "Posts are visible."),
            new BotAction(BotActionType.SCROLL, null, null, "Load the comments."),
            new BotAction(BotActionType.EXTRACT, null, null, "Comments are visible.")
        );
        llmClient.decideNextAction(SNAPSHOT, "Собери објави од 3 видеа", history);

        server.verify();
    }

    @Test
    void testPageIsScrolledTwiceBeforeItIsExtracted() {
        server.expect(requestTo(URL))
            .andRespond(withSuccess(answer("""
                {"action": "EXTRACT", "reasoning": "Posts are visible.", "goalReached": false}
                """), MediaType.APPLICATION_JSON));

        List<BotAction> history = List.of(
            new BotAction(BotActionType.NAVIGATE, "https://www.youtube.com/watch?v=1", null, "Open the video."),
            new BotAction(BotActionType.SCROLL, null, null, "Load the comments.")
        );
        BotDecision decision = llmClient.decideNextAction(SNAPSHOT, "Собери објави", history);

        assertThat(decision.action().type()).isEqualTo(BotActionType.SCROLL);
        assertThat(decision.goalReached()).isFalse();
    }

    @Test
    void testPageIsScrolledOnceMoreWhenTheModelLeavesAfterOneScroll() {
        server.expect(requestTo(URL))
            .andRespond(withSuccess(answer("""
                {"action": "NAVIGATE", "target": "https://www.youtube.com/@test", "reasoning": "Next video.", "goalReached": false}
                """), MediaType.APPLICATION_JSON));

        List<BotAction> history = List.of(
            new BotAction(BotActionType.NAVIGATE, "https://www.youtube.com/watch?v=1", null, "Open the video."),
            new BotAction(BotActionType.SCROLL, null, null, "Load the comments.")
        );
        BotDecision decision = llmClient.decideNextAction(SNAPSHOT, "Собери објави", history);

        assertThat(decision.action().type()).isEqualTo(BotActionType.SCROLL);
        assertThat(decision.action().target()).isNull();
    }

    @Test
    void testScrolledPostsAreExtractedBeforeLeavingThePage() {
        server.expect(requestTo(URL))
            .andRespond(withSuccess(answer("""
                {"action": "FINISH", "reasoning": "All videos are done.", "goalReached": true}
                """), MediaType.APPLICATION_JSON));

        List<BotAction> history = List.of(
            new BotAction(BotActionType.NAVIGATE, "https://www.youtube.com/watch?v=1", null, "Open the video."),
            new BotAction(BotActionType.SCROLL, null, null, "Load the comments."),
            new BotAction(BotActionType.SCROLL, null, null, "Load more comments.")
        );
        BotDecision decision = llmClient.decideNextAction(SNAPSHOT, "Собери објави", history);

        assertThat(decision.action().type()).isEqualTo(BotActionType.EXTRACT);
        assertThat(decision.goalReached()).isFalse();
    }

    @Test
    void testEarlyFinishIsAskedAgainWithTheRealNumbers() {
        server.expect(requestTo(URL))
            .andRespond(withSuccess(answer("""
                {"action": "FINISH", "reasoning": "All videos are done.", "goalReached": true}
                """), MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL))
            .andExpect(content().string(containsString("The goal needs 3 pages with extracted posts and there are only 1.")))
            .andRespond(withSuccess(answer("""
                {"action": "NAVIGATE", "target": "https://www.youtube.com/@test/videos", "reasoning": "Next video.", "goalReached": true}
                """), MediaType.APPLICATION_JSON));

        BotDecision decision = llmClient.decideNextAction(SNAPSHOT, GOAL_OF_THREE_PAGES, ONE_EXTRACTED_PAGE);

        assertThat(decision.action().type()).isEqualTo(BotActionType.NAVIGATE);
        assertThat(decision.action().target()).isEqualTo(START_PAGE);
        assertThat(decision.goalReached()).isFalse();
        server.verify();
    }

    @Test
    void testRunGoesBackToTheStartPageWhenTheModelStillFinishesEarly() {
        String finish = answer("""
            {"action": "FINISH", "reasoning": "All videos are done.", "goalReached": true}
            """);
        server.expect(requestTo(URL)).andRespond(withSuccess(finish, MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL)).andRespond(withSuccess(finish, MediaType.APPLICATION_JSON));

        List<BotAction> history = List.of(
            new BotAction(BotActionType.NAVIGATE, "https://www.youtube.com/@test/videos", null, "Open the channel."),
            new BotAction(BotActionType.NAVIGATE, "https://www.youtube.com/watch?v=1", null, "Open the first video."),
            new BotAction(BotActionType.SCROLL, null, null, "Load the comments."),
            new BotAction(BotActionType.SCROLL, null, null, "Load more comments."),
            new BotAction(BotActionType.EXTRACT, null, null, "Comments are visible.")
        );
        BotDecision decision = llmClient.decideNextAction(SNAPSHOT, GOAL_OF_THREE_PAGES, history);

        assertThat(decision.goalReached()).isFalse();
        assertThat(decision.action().type()).isEqualTo(BotActionType.NAVIGATE);
        assertThat(decision.action().target()).isEqualTo("https://www.youtube.com/@test/videos");
        assertThat(decision.action().reasoning()).startsWith("Not done yet: the goal needs 3 pages");
        server.verify();
    }

    @Test
    void testFinishIsAcceptedWhenTheGoalIsDone() {
        server.expect(requestTo(URL))
            .andRespond(withSuccess(answer("""
                {"action": "FINISH", "reasoning": "The video is done.", "goalReached": true}
                """), MediaType.APPLICATION_JSON));

        String goal = "Collect posts. FINISH only when the line PAGES WITH EXTRACTED POSTS shows 1.";
        BotDecision decision = llmClient.decideNextAction(SNAPSHOT, goal, ONE_EXTRACTED_PAGE);

        assertThat(decision.action().type()).isEqualTo(BotActionType.FINISH);
        assertThat(decision.goalReached()).isTrue();
        server.verify();
    }

    @Test
    void testRepeatedExtractionIsAskedAgain() {
        server.expect(requestTo(URL))
            .andRespond(withSuccess(answer("""
                {"action": "EXTRACT", "reasoning": "Comments are visible.", "goalReached": false}
                """), MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL))
            .andExpect(content().string(containsString("Do not EXTRACT again")))
            .andRespond(withSuccess(answer("""
                {"action": "NAVIGATE", "target": "https://www.youtube.com/@test/videos", "reasoning": "Next video.", "goalReached": false}
                """), MediaType.APPLICATION_JSON));

        List<BotAction> history = List.of(
            new BotAction(BotActionType.NAVIGATE, START_PAGE, null, "Open the channel."),
            new BotAction(BotActionType.NAVIGATE, "https://www.youtube.com/watch?v=1", null, "Open the video."),
            new BotAction(BotActionType.SCROLL, null, null, "Load the comments."),
            new BotAction(BotActionType.SCROLL, null, null, "Load more comments."),
            new BotAction(BotActionType.EXTRACT, null, null, "Comments are visible.")
        );
        BotDecision decision = llmClient.decideNextAction(SNAPSHOT, "Собери објави", history);

        assertThat(decision.action().type()).isEqualTo(BotActionType.NAVIGATE);
        server.verify();
    }

    @Test
    void testNextPageIsChosenOnTheStartPage() {
        server.expect(requestTo(URL))
            .andRespond(withSuccess(answer("""
                {"action": "NAVIGATE", "target": "https://www.youtube.com/watch?v=other", "reasoning": "Next video.", "goalReached": false}
                """), MediaType.APPLICATION_JSON));

        BotDecision decision = llmClient.decideNextAction(SNAPSHOT, GOAL_OF_THREE_PAGES, ONE_EXTRACTED_PAGE);

        assertThat(decision.action().type()).isEqualTo(BotActionType.NAVIGATE);
        assertThat(decision.action().target()).isEqualTo(START_PAGE);
        assertThat(decision.action().reasoning()).startsWith("Back to the start page");
        server.verify();
    }

    @Test
    void testUnknownActionIsRejected() {
        server.expect(requestTo(URL))
            .andRespond(withSuccess(answer("""
                {"action": "DANCE", "reasoning": "?", "goalReached": false}
                """), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> llmClient.decideNextAction(SNAPSHOT, "Собери објави", List.of()))
            .isInstanceOf(BotExecutionException.class);
    }

    @Test
    void testRetriesWhenRateLimited() {
        server.expect(requestTo(URL))
            .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\": {\"code\": 429, \"details\": [{\"retryDelay\": \"0s\"}]}}"));
        server.expect(requestTo(URL))
            .andRespond(withSuccess(answer("Здраво!"), MediaType.APPLICATION_JSON));

        assertThat(llmClient.complete("Одговори кратко.", "Кажи здраво.")).isEqualTo("Здраво!");
        server.verify();
    }

    private String answer(String text) {
        String escaped = objectMapper.valueToTree(text.strip()).toString();
        return """
            {"candidates": [{"content": {"role": "model", "parts": [{"text": %s}]}, "finishReason": "STOP"}]}
            """.formatted(escaped);
    }
}
