package mk.ukim.finki.aibotbackend.bot.extraction;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import mk.ukim.finki.aibotbackend.bot.browser.PageSnapshot;
import mk.ukim.finki.aibotbackend.model.dto.CreateExtractedPostDto;
import mk.ukim.finki.aibotbackend.model.dto.CreateMediaItemDto;
import mk.ukim.finki.aibotbackend.model.enums.MediaType;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/**
 * Extracts content from YouTube video pages: the video itself (its title and description, with the video
 * and its thumbnail as media) and every comment that is loaded on the page. Channel and hashtag pages only
 * list videos, so there is nothing to extract on them, the bot opens the videos first.
 */
@Component
public class YouTubeContentExtractor implements ContentExtractor {
    private static final String WATCH_URL = "https://www.youtube.com/watch?v=";
    private static final Pattern VIDEO_ID = Pattern.compile("[?&]v=([\\w-]{11})");
    private static final Pattern COMMENT_ID = Pattern.compile("[?&]lc=([\\w-]+)");
    private static final Pattern HANDLE = Pattern.compile("/(@[^/?#]+)");
    private static final Pattern INVISIBLE_CHARACTERS = Pattern.compile("[\\uFE0E\\uFE0F\\u200B-\\u200D\\u2060]");

    @Override
    public List<CreateExtractedPostDto> extract(PageSnapshot snapshot) {
        Matcher videoId = VIDEO_ID.matcher(snapshot.url() == null ? "" : snapshot.url());
        if (!videoId.find() || snapshot.domContent() == null) {
            return List.of();
        }
        String videoUrl = WATCH_URL + videoId.group(1);
        Document page = Jsoup.parseBodyFragment(snapshot.domContent());

        List<CreateExtractedPostDto> posts = new ArrayList<>();
        videoPost(page, videoId.group(1), videoUrl).ifPresent(posts::add);
        for (Element comment : page.select("#comment")) {
            commentPost(comment, videoUrl).ifPresent(posts::add);
        }
        return posts;
    }

    private Optional<CreateExtractedPostDto> videoPost(Document page, String videoId, String videoUrl) {
        String title = textOf(page.selectFirst("#title h1"));
        // the whole description is on the page only after "...more" is clicked, otherwise its beginning is used
        String description = textOf(page.selectFirst("#description-inline-expander #expanded"));
        if (description.isEmpty()) {
            description = textOf(page.selectFirst("#description-inline-expander #snippet-text"));
        }
        String content = Stream.of(title, description)
            .filter(part -> !part.isEmpty())
            .distinct()
            .collect(Collectors.joining("\n\n"));
        if (content.isEmpty()) {
            return Optional.empty();
        }

        List<CreateMediaItemDto> media = List.of(
            new CreateMediaItemDto(MediaType.VIDEO, videoUrl, null),
            new CreateMediaItemDto(MediaType.IMAGE, "https://i.ytimg.com/vi/%s/hqdefault.jpg".formatted(videoId), null)
        );
        String channel = handleOf(page.selectFirst("#owner a[href]"));
        return Optional.of(new CreateExtractedPostDto("video:" + videoId, channel, content, videoUrl, null, null, media));
    }

    private Optional<CreateExtractedPostDto> commentPost(Element comment, String videoUrl) {
        String content = textOf(comment.selectFirst("#expander #content"));
        if (content.isEmpty()) {
            return Optional.empty();
        }
        // the link on the date of a comment points to the comment itself
        Optional<String> commentId = comment.select("a[href]")
            .stream()
            .map(link -> COMMENT_ID.matcher(link.attr("href")))
            .filter(Matcher::find)
            .map(matcher -> matcher.group(1))
            .findFirst();
        String externalId = commentId.map(id -> "comment:" + id).orElse(null);
        String sourceUrl = commentId.map(id -> videoUrl + "&lc=" + id).orElse(videoUrl);
        String author = handleOf(comment.selectFirst("#header-author a[href]"));
        return Optional.of(new CreateExtractedPostDto(externalId, author, content, sourceUrl, null, null, List.of()));
    }

    private static String handleOf(Element link) {
        if (link == null) {
            return null;
        }
        Matcher handle = HANDLE.matcher(link.attr("href"));
        if (handle.find()) {
            return URLDecoder.decode(handle.group(1), StandardCharsets.UTF_8);
        }
        String text = link.text().strip();
        return text.isEmpty() ? null : text;
    }

    private static String textOf(Element element) {
        if (element == null) {
            return "";
        }
        return Arrays.stream(element.wholeText().split("\n"))
            // emojis are images on YouTube, their invisible variation selectors stay behind in the text
            .map(line -> INVISIBLE_CHARACTERS.matcher(line).replaceAll("").replaceAll("\\s+", " ").strip())
            .filter(line -> !line.isEmpty())
            .collect(Collectors.joining("\n"));
    }
}
