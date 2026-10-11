package mk.ukim.finki.aibotbackend.bot.browser;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import mk.ukim.finki.aibotbackend.config.BotProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the agent against a local test page in a headless Chrome, so no network is needed.
 */
public class SeleniumBrowserAgentTest {
    private static SeleniumBrowserAgent browserAgent;
    private static String testPageUrl;

    @BeforeAll
    static void startBrowser() throws URISyntaxException {
        browserAgent = new SeleniumBrowserAgent(new BotProperties(10, true));
        browserAgent.start();
        testPageUrl = Path.of(SeleniumBrowserAgentTest.class.getResource("/pages/browser-test.html").toURI())
            .toUri()
            .toString();
    }

    @AfterAll
    static void closeBrowser() {
        browserAgent.close();
    }

    @BeforeEach
    void openTestPage() {
        browserAgent.navigateTo(testPageUrl);
    }

    @Test
    void testSnapshotContainsOnlyVisibleContent() {
        PageSnapshot snapshot = browserAgent.snapshot();

        assertThat(snapshot.title()).isEqualTo("Тест страница");
        assertThat(snapshot.url()).startsWith("file:");
        assertThat(snapshot.domContent()).contains("Македонски објави", "data-bot-id");
        assertThat(snapshot.domContent()).doesNotContain("Скриена објава");
    }

    @Test
    void testSnapshotKeepsLineBreaksOfTextShownInLines() {
        assertThat(browserAgent.snapshot().domContent()).contains("Прв ред на описот.\nВтор ред на описот.");
    }

    @Test
    void testClickByVisibleText() {
        browserAgent.click("Прикажи повеќе");

        assertThat(browserAgent.snapshot().domContent()).contains("Скриена објава за тестирање.");
    }

    @Test
    void testClickByNumberFromSnapshot() {
        Matcher matcher = Pattern
            .compile("data-bot-id=\"(\\d+)\"[^>]*>Прикажи повеќе")
            .matcher(browserAgent.snapshot().domContent());
        assertThat(matcher.find()).isTrue();

        browserAgent.click("[" + matcher.group(1) + "]");

        assertThat(browserAgent.snapshot().domContent()).contains("Скриена објава за тестирање.");
    }

    @Test
    void testTypeWithNewlineSubmitsTheForm() {
        browserAgent.type("Пребарај", "Скопје\n");

        assertThat(browserAgent.snapshot().domContent()).contains("Барано: Скопје");
    }

    @Test
    void testScrollDownMovesThePage() {
        browserAgent.scrollDown();

        assertThat(browserAgent.snapshot().domContent()).doesNotStartWith("<!-- scroll 0 of");
    }

    @Test
    void testScreenshotIsPng() {
        byte[] screenshot = browserAgent.takeScreenshot();

        assertThat(screenshot).startsWith((byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G');
    }
}
