package mk.ukim.finki.aibotbackend.bot.browser;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import mk.ukim.finki.aibotbackend.config.BotProperties;
import org.openqa.selenium.By;
import org.openqa.selenium.ElementNotInteractableException;
import org.openqa.selenium.InvalidElementStateException;
import org.openqa.selenium.InvalidSelectorException;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Component;

// TODO(student): Replace this bean with your Playwright/Selenium-backed implementation.
/**
 * {@link BrowserAgent} backed by Selenium and Chrome.
 *
 * <p>An element can be described with the number from the latest snapshot ("12" or "[12]"),
 * with a CSS selector or XPath, or simply with its visible text or label ("Прикажи повеќе").
 * Text typed with a trailing newline is submitted with Enter.</p>
 */
@Slf4j
@Component
public class SeleniumBrowserAgent implements BrowserAgent {
    // big enough for a video page with all of its loaded comments, the LLM gets a shorter view of it
    private static final int MAX_DOM_LENGTH = 200_000;
    private static final int SCROLL_HOPS = 3;
    private static final Pattern ELEMENT_NUMBER = Pattern.compile("^\\[?(\\d+)]?$");
    private static final String SNAPSHOT_SCRIPT = readScript("bot/snapshot.js");
    private static final String FIND_ELEMENT_SCRIPT = readScript("bot/find-element.js");

    private final BotProperties botProperties;
    private ChromeDriver driver;

    public SeleniumBrowserAgent(BotProperties botProperties) {
        this.botProperties = botProperties;
    }

    @Override
    public synchronized void start() {
        // TODO(student): Implement BrowserAgent.start().
        if (driver != null) {
            return;
        }
        ChromeOptions options = new ChromeOptions();
        if (!Boolean.FALSE.equals(botProperties.headless())) {
            options.addArguments("--headless=new");
        }
        options.addArguments("--window-size=1366,900", "--lang=mk", "--disable-notifications", "--mute-audio");

        driver = new ChromeDriver(options);
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
        log.info("Browser started (headless: {}).", !Boolean.FALSE.equals(botProperties.headless()));
    }

    @Override
    public void navigateTo(String url) {
        // TODO(student): Implement BrowserAgent.navigateTo().
        String absoluteUrl = absolute(url.strip());
        log.debug("Navigating to {}", absoluteUrl);
        browser().navigate().to(absoluteUrl);
        waitForPage();
    }

    @Override
    public void click(String elementDescription) {
        // TODO(student): Implement BrowserAgent.click().
        WebElement element = find(elementDescription, false);
        scrollIntoView(element);
        // links that open a new tab would leave the bot looking at the old one
        browser().executeScript("arguments[0].removeAttribute('target');", element);
        try {
            element.click();
        } catch (ElementNotInteractableException e) {
            // covered by another element (a banner, a popup...) or not clickable the normal way
            browser().executeScript("arguments[0].click();", element);
        }
        waitForPage();
    }

    @Override
    public void type(String elementDescription, String text) {
        // TODO(student): Implement BrowserAgent.type().
        WebElement element = find(elementDescription, true);
        scrollIntoView(element);

        String value = text == null ? "" : text;
        boolean submit = value.endsWith("\n");
        value = value.replaceAll("[\\r\\n]+$", "");

        try {
            element.clear();
        } catch (InvalidElementStateException e) {
            element.sendKeys(Keys.chord(Keys.CONTROL, "a"), Keys.DELETE);
        }
        element.sendKeys(value);
        if (submit) {
            element.sendKeys(Keys.ENTER);
            waitForPage();
        }
    }

    @Override
    public void scrollDown() {
        // TODO(student): Implement BrowserAgent.scrollDown().
        // a few short hops instead of one jump, so that content that loads when it comes into view gets its turn
        for (int hop = 0; hop < SCROLL_HOPS; hop++) {
            browser().executeScript("window.scrollBy(0, Math.round(window.innerHeight * 0.85));");
            pause(700);
        }
        pause(1500);
    }

    @Override
    public byte[] takeScreenshot() {
        // TODO(student): Implement BrowserAgent.takeScreenshot().
        return browser().getScreenshotAs(OutputType.BYTES);
    }

    @Override
    public PageSnapshot snapshot() {
        // TODO(student): Implement BrowserAgent.snapshot().
        ChromeDriver browser = browser();
        String domContent = (String) browser.executeScript(SNAPSHOT_SCRIPT, MAX_DOM_LENGTH);
        return new PageSnapshot(browser.getCurrentUrl(), browser.getTitle(), domContent, null);
    }

    @Override
    public synchronized void close() {
        // TODO(student): Implement BrowserAgent.close().
        if (driver == null) {
            return;
        }
        try {
            driver.quit();
        } finally {
            driver = null;
            log.info("Browser closed.");
        }
    }

    private ChromeDriver browser() {
        if (driver == null) {
            start();
        }
        return driver;
    }

    // the model sometimes writes links the way they appear on the page, e.g. "/watch?v=..." or "www.youtube.com/..."
    private String absolute(String url) {
        if (url.startsWith("/") && !url.startsWith("//")) {
            URI current = URI.create(browser().getCurrentUrl());
            if (current.getAuthority() != null) {
                return current.getScheme() + "://" + current.getAuthority() + url;
            }
        }
        if (!url.matches("(?i)^[a-z][a-z0-9+.-]*:.*")) {
            return "https://" + url.replaceFirst("^//", "");
        }
        return url;
    }

    private WebElement find(String description, boolean input) {
        if (description == null || description.isBlank()) {
            throw new NoSuchElementException("No element description was given.");
        }
        String trimmed = description.strip();

        Matcher number = ELEMENT_NUMBER.matcher(trimmed);
        if (number.matches()) {
            List<WebElement> tagged = browser().findElements(By.cssSelector("[data-bot-id='%s']".formatted(number.group(1))));
            if (!tagged.isEmpty()) {
                return tagged.getFirst();
            }
        }

        By selector = trimmed.startsWith("/") || trimmed.startsWith("(") ? By.xpath(trimmed) : By.cssSelector(trimmed);
        try {
            for (WebElement element : browser().findElements(selector)) {
                if (element.isDisplayed()) {
                    return element;
                }
            }
        } catch (InvalidSelectorException ignored) {
            // not a selector, so it is looked up by its visible text below
        }

        Object match = browser().executeScript(FIND_ELEMENT_SCRIPT, trimmed, input);
        if (match instanceof WebElement element) {
            return element;
        }
        throw new NoSuchElementException("Could not find element: " + trimmed);
    }

    private void scrollIntoView(WebElement element) {
        browser().executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
    }

    private void waitForPage() {
        try {
            new WebDriverWait(browser(), Duration.ofSeconds(15))
                .until(webDriver -> "complete".equals(browser().executeScript("return document.readyState;")));
        } catch (TimeoutException e) {
            log.debug("Page did not finish loading in 15 seconds, continuing anyway.");
        }
        // single page apps keep rendering after the load event
        pause(1000);
    }

    private void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String readScript(String path) {
        try (InputStream stream = SeleniumBrowserAgent.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Missing browser script: " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
