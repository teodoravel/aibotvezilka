package mk.ukim.finki.aibotbackend.bot.core;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import mk.ukim.finki.aibotbackend.model.domain.ExtractedPost;
import mk.ukim.finki.aibotbackend.model.domain.ExtractionSession;
import mk.ukim.finki.aibotbackend.model.domain.ExtractionTarget;
import mk.ukim.finki.aibotbackend.model.dto.CreateExtractedPostDto;
import mk.ukim.finki.aibotbackend.model.enums.SessionStatus;
import mk.ukim.finki.aibotbackend.model.exception.SessionNotFoundException;
import mk.ukim.finki.aibotbackend.service.domain.BotActionLogService;
import mk.ukim.finki.aibotbackend.service.domain.ExtractedPostService;
import mk.ukim.finki.aibotbackend.service.domain.ExtractionSessionService;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class BotOrchestratorImpl implements BotOrchestrator {
    private final SocialNetworkBot socialNetworkBot;
    private final ExtractionSessionService extractionSessionService;
    private final ExtractedPostService extractedPostService;
    private final BotActionLogService botActionLogService;

    public BotOrchestratorImpl(
        SocialNetworkBot socialNetworkBot,
        ExtractionSessionService extractionSessionService,
        ExtractedPostService extractedPostService,
        BotActionLogService botActionLogService
    ) {
        this.socialNetworkBot = socialNetworkBot;
        this.extractionSessionService = extractionSessionService;
        this.extractedPostService = extractedPostService;
        this.botActionLogService = botActionLogService;
    }

    /**
     * Runs one session from start to end. There is only one browser, so sessions run one at a time.
     * A session stopped by the user ends after the action that is in progress.
     */
    @Override
    public synchronized void runSession(Long sessionId) {
        // TODO(student): Orchestrate the full run:
        //  1. socialNetworkBot.login()
        //  2. for each target of the session:
        //       socialNetworkBot.execute(target,
        //           (action, successful) -> botActionLogService.log(session, action, successful))
        //     then map the returned DTOs with CreateExtractedPostDto.toExtractedPost(session)
        //     and persist them with extractedPostService.saveAll(...)
        //  3. mark the session COMPLETED via extractionSessionService.complete(sessionId),
        //     or FAILED via extractionSessionService.fail(sessionId) when something goes wrong
        //  4. always socialNetworkBot.shutdown() at the end
        ExtractionSession session = extractionSessionService
            .findById(sessionId)
            .orElseThrow(() -> new SessionNotFoundException(sessionId));
        if (session.getStatus() != SessionStatus.RUNNING) {
            log.info("Session {} is {}, so the bot does not run it.", sessionId, session.getStatus());
            return;
        }

        Set<String> seenPosts = new HashSet<>();
        try {
            socialNetworkBot.login();
            for (ExtractionTarget target : session.getTargets()) {
                List<CreateExtractedPostDto> collected = socialNetworkBot.execute(target, (action, successful) -> {
                    botActionLogService.log(session, action, successful);
                    if (isStopped(sessionId)) {
                        throw new SessionStoppedException();
                    }
                });

                // the same post can be extracted twice when the page did not scroll far enough
                List<ExtractedPost> posts = collected.stream()
                    .filter(post -> seenPosts.add(keyOf(post)))
                    .map(post -> post.toExtractedPost(session))
                    .toList();
                extractedPostService.saveAll(posts);
                log.info("Session {}: {} new posts for {} '{}'.", sessionId, posts.size(), target.getType(), target.getValue());
            }
            if (!isStopped(sessionId)) {
                extractionSessionService.complete(sessionId);
            }
        } catch (SessionStoppedException e) {
            log.info("Session {} was stopped, the posts of the unfinished target are not saved.", sessionId);
        } catch (RuntimeException e) {
            log.error("Session {} failed.", sessionId, e);
            extractionSessionService.fail(sessionId);
        } finally {
            socialNetworkBot.shutdown();
        }
    }

    private boolean isStopped(Long sessionId) {
        return extractionSessionService
            .findById(sessionId)
            .map(session -> session.getStatus() != SessionStatus.RUNNING)
            .orElse(true);
    }

    private static String keyOf(CreateExtractedPostDto post) {
        return post.externalId() != null ? post.externalId() : post.sourceUrl() + "|" + post.content();
    }

    /**
     * Thrown from the step listener to leave the bot loop once the user stops the session.
     */
    private static class SessionStoppedException extends RuntimeException {
    }
}
