package mk.ukim.finki.aibotbackend.service;

import java.util.Optional;
import mk.ukim.finki.aibotbackend.model.domain.ExtractionSession;
import mk.ukim.finki.aibotbackend.model.enums.SessionStatus;
import mk.ukim.finki.aibotbackend.model.enums.SocialNetwork;
import mk.ukim.finki.aibotbackend.model.exception.InvalidSessionStateException;
import mk.ukim.finki.aibotbackend.model.exception.SessionNotFoundException;
import mk.ukim.finki.aibotbackend.repository.ExtractionSessionRepository;
import mk.ukim.finki.aibotbackend.service.domain.impl.ExtractionSessionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ExtractionSessionServiceImplTest {
    @Mock
    private ExtractionSessionRepository extractionSessionRepository;

    @InjectMocks
    private ExtractionSessionServiceImpl extractionSessionService;

    private ExtractionSession session;

    @BeforeEach
    void setUp() {
        session = new ExtractionSession(SocialNetwork.YOUTUBE, "Тест сесија");
        lenient().when(extractionSessionRepository.findById(1L)).thenReturn(Optional.of(session));
        lenient().when(extractionSessionRepository.save(any(ExtractionSession.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void testStartMovesACreatedSessionToRunning() {
        ExtractionSession started = extractionSessionService.start(1L);

        assertThat(started.getStatus()).isEqualTo(SessionStatus.RUNNING);
        assertThat(started.getStartedAt()).isNotNull();
    }

    @Test
    void testFinishedSessionCannotBeStartedAgain() {
        session.setStatus(SessionStatus.COMPLETED);

        assertThatThrownBy(() -> extractionSessionService.start(1L))
            .isInstanceOf(InvalidSessionStateException.class);
    }

    @Test
    void testStopAndCompleteNeedARunningSession() {
        assertThatThrownBy(() -> extractionSessionService.stop(1L))
            .isInstanceOf(InvalidSessionStateException.class);
        assertThatThrownBy(() -> extractionSessionService.complete(1L))
            .isInstanceOf(InvalidSessionStateException.class);
    }

    @Test
    void testCompleteStampsTheFinishTime() {
        session.setStatus(SessionStatus.RUNNING);

        ExtractionSession completed = extractionSessionService.complete(1L);

        assertThat(completed.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(completed.getFinishedAt()).isNotNull();
    }

    @Test
    void testFailWorksFromAnyStatus() {
        session.setStatus(SessionStatus.PAUSED);

        assertThat(extractionSessionService.fail(1L).getStatus()).isEqualTo(SessionStatus.FAILED);
    }

    @Test
    void testMissingSessionThrows() {
        when(extractionSessionRepository.findById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> extractionSessionService.start(2L))
            .isInstanceOf(SessionNotFoundException.class);
    }
}
