package mk.ukim.finki.aibotbackend.service.application.impl;

import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import mk.ukim.finki.aibotbackend.events.SessionStartedEvent;
import mk.ukim.finki.aibotbackend.model.domain.ExtractionSession;
import mk.ukim.finki.aibotbackend.model.dto.CreateExtractionSessionDto;
import mk.ukim.finki.aibotbackend.model.dto.DisplayBotActionLogDto;
import mk.ukim.finki.aibotbackend.model.dto.DisplayExtractionSessionDto;
import mk.ukim.finki.aibotbackend.service.application.ExtractionSessionApplicationService;
import mk.ukim.finki.aibotbackend.service.domain.BotActionLogService;
import mk.ukim.finki.aibotbackend.service.domain.ExtractionSessionService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class ExtractionSessionApplicationServiceImpl implements ExtractionSessionApplicationService {
    private final ExtractionSessionService extractionSessionService;
    private final BotActionLogService botActionLogService;
    private final ApplicationEventPublisher applicationEventPublisher;

    public ExtractionSessionApplicationServiceImpl(
        ExtractionSessionService extractionSessionService,
        BotActionLogService botActionLogService,
        ApplicationEventPublisher applicationEventPublisher
    ) {
        this.extractionSessionService = extractionSessionService;
        this.botActionLogService = botActionLogService;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public List<DisplayExtractionSessionDto> findAll() {
        // TODO(student): Implement ExtractionSessionApplicationService.findAll().
        return DisplayExtractionSessionDto.from(extractionSessionService.findAll());
    }

    @Override
    public Optional<DisplayExtractionSessionDto> findById(Long id) {
        // TODO(student): Implement ExtractionSessionApplicationService.findById().
        return extractionSessionService
            .findById(id)
            .map(DisplayExtractionSessionDto::from);
    }

    @Override
    public DisplayExtractionSessionDto create(CreateExtractionSessionDto createExtractionSessionDto) {
        // TODO(student): Map the DTO to an entity (toExtractionSession), delegate to
        //  the domain service and map the result back (DisplayExtractionSessionDto.from).
        ExtractionSession session = extractionSessionService.create(createExtractionSessionDto.toExtractionSession());
        return DisplayExtractionSessionDto.from(session);
    }

    @Override
    @Transactional
    public DisplayExtractionSessionDto start(Long id) {
        // TODO(student): Start the session via the domain service, then publish
        //  new SessionStartedEvent(id) with applicationEventPublisher — the
        //  SessionStartedListener picks it up and runs the bot asynchronously.
        //  This method needs to run in a transaction for the AFTER_COMMIT
        //  listener to fire (see jakarta.transaction.Transactional).
        ExtractionSession session = extractionSessionService.start(id);
        // the listener runs the bot only after this transaction commits
        applicationEventPublisher.publishEvent(new SessionStartedEvent(id));
        return DisplayExtractionSessionDto.from(session);
    }

    @Override
    public DisplayExtractionSessionDto stop(Long id) {
        // TODO(student): Implement ExtractionSessionApplicationService.stop().
        return DisplayExtractionSessionDto.from(extractionSessionService.stop(id));
    }

    @Override
    public List<DisplayBotActionLogDto> findLogsBySessionId(Long id) {
        return DisplayBotActionLogDto.from(botActionLogService.findBySessionId(id));
    }
}
