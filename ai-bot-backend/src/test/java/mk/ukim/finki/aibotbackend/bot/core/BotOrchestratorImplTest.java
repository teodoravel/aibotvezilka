package mk.ukim.finki.aibotbackend.bot.core;

import java.util.List;
import java.util.Optional;
import mk.ukim.finki.aibotbackend.bot.llm.BotAction;
import mk.ukim.finki.aibotbackend.model.domain.ExtractedPost;
import mk.ukim.finki.aibotbackend.model.domain.ExtractionSession;
import mk.ukim.finki.aibotbackend.model.domain.ExtractionTarget;
import mk.ukim.finki.aibotbackend.model.dto.CreateExtractedPostDto;
import mk.ukim.finki.aibotbackend.model.enums.BotActionType;
import mk.ukim.finki.aibotbackend.model.enums.SessionStatus;
import mk.ukim.finki.aibotbackend.model.enums.SocialNetwork;
import mk.ukim.finki.aibotbackend.model.enums.TargetType;
import mk.ukim.finki.aibotbackend.model.exception.BotExecutionException;
import mk.ukim.finki.aibotbackend.service.domain.BotActionLogService;
import mk.ukim.finki.aibotbackend.service.domain.ExtractedPostService;
import mk.ukim.finki.aibotbackend.service.domain.ExtractionSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class BotOrchestratorImplTest {
    @Mock
    private SocialNetworkBot socialNetworkBot;
    @Mock
    private ExtractionSessionService extractionSessionService;
    @Mock
    private ExtractedPostService extractedPostService;
    @Mock
    private BotActionLogService botActionLogService;
    @Captor
    private ArgumentCaptor<List<ExtractedPost>> savedPosts;

    @InjectMocks
    private BotOrchestratorImpl botOrchestrator;

    private ExtractionSession session;

    @BeforeEach
    void setUp() {
        session = new ExtractionSession(SocialNetwork.YOUTUBE, "Тест сесија");
        session.setStatus(SessionStatus.RUNNING);
        session.getTargets().add(new ExtractionTarget(TargetType.PROFILE, "@kanal", session));
        session.getTargets().add(new ExtractionTarget(TargetType.KEYWORD, "Охрид", session));
        lenient().when(extractionSessionService.findById(1L)).thenReturn(Optional.of(session));
    }

    @Test
    void testSuccessfulRunSavesNewPostsAndCompletes() {
        when(socialNetworkBot.execute(any(), any())).thenReturn(List.of(post("p1"), post("p1"), post("p2")));

        botOrchestrator.runSession(1L);

        verify(socialNetworkBot).login();
        verify(socialNetworkBot, times(2)).execute(any(), any());
        verify(extractedPostService, times(2)).saveAll(savedPosts.capture());
        // the duplicate in the first target and the same posts in the second target are skipped
        assertThat(savedPosts.getAllValues().get(0)).hasSize(2);
        assertThat(savedPosts.getAllValues().get(1)).isEmpty();
        verify(extractionSessionService).complete(1L);
        verify(extractionSessionService, never()).fail(anyLong());
        verify(socialNetworkBot).shutdown();
    }

    @Test
    void testFailingRunMarksTheSessionFailed() {
        when(socialNetworkBot.execute(any(), any())).thenThrow(new BotExecutionException("Gemini is not available"));

        botOrchestrator.runSession(1L);

        verify(extractionSessionService).fail(1L);
        verify(extractionSessionService, never()).complete(anyLong());
        verify(socialNetworkBot).shutdown();
    }

    @Test
    void testStoppedSessionEndsWithoutCompletingOrFailing() {
        ExtractionSession paused = new ExtractionSession(SocialNetwork.YOUTUBE, "Тест сесија");
        paused.setStatus(SessionStatus.PAUSED);
        when(extractionSessionService.findById(1L)).thenReturn(Optional.of(session), Optional.of(paused));
        when(socialNetworkBot.execute(any(), any())).thenAnswer(invocation -> {
            BotStepListener listener = invocation.getArgument(1);
            listener.onStep(new BotAction(BotActionType.SCROLL, null, null, "Load more posts."), true);
            return List.of(post("p1"));
        });

        botOrchestrator.runSession(1L);

        verify(botActionLogService).log(eq(session), any(), eq(true));
        verify(socialNetworkBot, times(1)).execute(any(), any());
        verify(extractedPostService, never()).saveAll(any());
        verify(extractionSessionService, never()).complete(anyLong());
        verify(extractionSessionService, never()).fail(anyLong());
        verify(socialNetworkBot).shutdown();
    }

    @Test
    void testSessionThatIsNotRunningIsSkipped() {
        session.setStatus(SessionStatus.COMPLETED);

        botOrchestrator.runSession(1L);

        verify(socialNetworkBot, never()).login();
        verify(socialNetworkBot, never()).execute(any(), any());
    }

    private static CreateExtractedPostDto post(String externalId) {
        return new CreateExtractedPostDto(
            externalId,
            "@avtor",
            "Ова е објава број " + externalId + " од македонски канал.",
            "https://www.youtube.com/watch?v=" + externalId,
            null,
            0.9,
            List.of()
        );
    }
}
