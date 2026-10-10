package mk.ukim.finki.aibotbackend.bot.core;

import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import mk.ukim.finki.aibotbackend.bot.browser.BrowserAgent;
import mk.ukim.finki.aibotbackend.bot.extraction.ContentExtractor;
import mk.ukim.finki.aibotbackend.bot.extraction.LanguageDetector;
import mk.ukim.finki.aibotbackend.bot.llm.LlmClient;
import mk.ukim.finki.aibotbackend.config.BotProperties;
import mk.ukim.finki.aibotbackend.model.domain.ExtractionTarget;
import mk.ukim.finki.aibotbackend.model.enums.SocialNetwork;
import org.springframework.stereotype.Component;

/**
 * The bot for YouTube. Public channels, hashtags and videos can be read without an account, so there is
 * nothing to log in to. The search page is never used, because YouTube's robots.txt does not allow it
 * for bots, so a keyword is looked up through the hashtag page with the same name.
 */
@Slf4j
@Component
public class YouTubeBot extends AbstractSocialNetworkBot {
    private static final String YOUTUBE_URL = "https://www.youtube.com";
    private static final int VIDEOS_PER_TARGET = 3;
    private static final String ON_A_VIDEO_PAGE = """
        On a video page: if the description under the video ends with "...more", click it first. \
        Then EXTRACT. Then SCROLL down and EXTRACT again, three times, so that the comments are loaded \
        and collected. Always EXTRACT after the last SCROLL.""";

    public YouTubeBot(
        BrowserAgent browserAgent,
        LlmClient llmClient,
        ContentExtractor contentExtractor,
        LanguageDetector languageDetector,
        BotProperties botProperties
    ) {
        super(browserAgent, llmClient, contentExtractor, languageDetector, botProperties);
    }

    @Override
    public SocialNetwork network() {
        return SocialNetwork.YOUTUBE;
    }

    /**
     * Public YouTube content needs no account, so this only opens YouTube
     * and rejects the optional cookies when a consent dialog is shown.
     */
    @Override
    public void login() {
        browserAgent.navigateTo(YOUTUBE_URL);
        for (String button : List.of("Reject all", "Одбиј ги сите")) {
            try {
                browserAgent.click(button);
                log.info("Rejected the optional YouTube cookies.");
                return;
            } catch (RuntimeException ignored) {
                // there is no consent dialog in this language, or none at all
            }
        }
    }

    @Override
    protected String buildGoal(ExtractionTarget target) {
        String value = target.getValue().strip();
        return switch (target.getType()) {
            case PROFILE -> listGoal("the YouTube channel " + value, channelUrl(value));
            case HASHTAG -> listGoal("the YouTube videos tagged #" + withoutHash(value), hashtagUrl(value));
            case KEYWORD -> listGoal("YouTube videos about \"" + value + "\"", hashtagUrl(value));
            case FEED_URL -> """
                Collect public Macedonian texts from %s. Start by opening that page. \
                If it is a video, do the steps below on it and then FINISH. \
                If it lists videos, open at most %d different ones, one at a time with NAVIGATE to their links, \
                and do the steps below on each.
                %s
                Never use the YouTube search.""".formatted(value, VIDEOS_PER_TARGET, ON_A_VIDEO_PAGE);
        };
    }

    private static String listGoal(String what, String url) {
        // videos are opened by their links, element numbers change on every page load,
        // so only links show in the history which videos are already done
        return """
            Collect public Macedonian texts from %s. Start at %s and open its videos one at a time, at most %d, \
            newest first. Open each video with NAVIGATE to its link, never open the same video twice. \
            After each video go back to %s for the next one, and FINISH when %d different videos are done.
            %s
            Never use the YouTube search.""".formatted(what, url, VIDEOS_PER_TARGET, url, VIDEOS_PER_TARGET, ON_A_VIDEO_PAGE);
    }

    private static String channelUrl(String value) {
        if (value.startsWith("http")) {
            return value;
        }
        String handle = value.startsWith("@") ? value : "@" + value;
        return YOUTUBE_URL + "/" + handle + "/videos";
    }

    private static String hashtagUrl(String value) {
        String tag = withoutHash(value).replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        return YOUTUBE_URL + "/hashtag/" + tag;
    }

    private static String withoutHash(String value) {
        return value.startsWith("#") ? value.substring(1) : value;
    }
}
