package mk.ukim.finki.aibotbackend.bot.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
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
            new BotAction(BotActionType.SCROLL, null, null, "Load the posts.")
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
    void testPageIsScrolledBeforeTheFirstExtraction() {
        server.expect(requestTo(URL))
            .andRespond(withSuccess(answer("""
                {"action": "EXTRACT", "reasoning": "Posts are visible.", "goalReached": false}
                """), MediaType.APPLICATION_JSON));

        List<BotAction> history = List.of(
            new BotAction(BotActionType.NAVIGATE, "https://www.youtube.com/watch?v=1", null, "Open the video.")
        );
        BotDecision decision = llmClient.decideNextAction(SNAPSHOT, "Собери објави", history);

        assertThat(decision.action().type()).isEqualTo(BotActionType.SCROLL);
        assertThat(decision.goalReached()).isFalse();
    }

    @Test
    void testScrolledPostsAreExtractedBeforeLeavingThePage() {
        server.expect(requestTo(URL))
            .andRespond(withSuccess(answer("""
                {"action": "FINISH", "reasoning": "All videos are done.", "goalReached": true}
                """), MediaType.APPLICATION_JSON));

        List<BotAction> history = List.of(
            new BotAction(BotActionType.NAVIGATE, "https://www.youtube.com/watch?v=1", null, "Open the video."),
            new BotAction(BotActionType.SCROLL, null, null, "Load the comments.")
        );
        BotDecision decision = llmClient.decideNextAction(SNAPSHOT, "Собери објави", history);

        assertThat(decision.action().type()).isEqualTo(BotActionType.EXTRACT);
        assertThat(decision.goalReached()).isFalse();
    }

    @Test
    void testFinishHasToBeSaidTwice() {
        String finish = answer("""
            {"action": "FINISH", "reasoning": "All videos are done.", "goalReached": true}
            """);
        server.expect(requestTo(URL)).andRespond(withSuccess(finish, MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL)).andRespond(withSuccess(finish, MediaType.APPLICATION_JSON));

        List<BotAction> history = new ArrayList<>(List.of(
            new BotAction(BotActionType.NAVIGATE, "https://www.youtube.com/watch?v=1", null, "Open the video."),
            new BotAction(BotActionType.SCROLL, null, null, "Load the comments."),
            new BotAction(BotActionType.EXTRACT, null, null, "Comments are visible.")
        ));
        BotDecision first = llmClient.decideNextAction(SNAPSHOT, "Собери објави од 3 видеа", history);

        assertThat(first.goalReached()).isFalse();
        assertThat(first.action().type()).isEqualTo(BotActionType.WAIT);
        assertThat(first.action().reasoning()).endsWith("Pages with extracted posts so far: 1.");

        history.add(first.action());
        BotDecision second = llmClient.decideNextAction(SNAPSHOT, "Собери објави од 3 видеа", history);

        assertThat(second.goalReached()).isTrue();
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
