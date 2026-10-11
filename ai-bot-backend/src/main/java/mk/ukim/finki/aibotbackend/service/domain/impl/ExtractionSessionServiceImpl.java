package mk.ukim.finki.aibotbackend.service.domain.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import mk.ukim.finki.aibotbackend.model.domain.ExtractionSession;
import mk.ukim.finki.aibotbackend.model.enums.SessionStatus;
import mk.ukim.finki.aibotbackend.model.exception.InvalidSessionStateException;
import mk.ukim.finki.aibotbackend.model.exception.SessionNotFoundException;
import mk.ukim.finki.aibotbackend.repository.ExtractionSessionRepository;
import mk.ukim.finki.aibotbackend.service.domain.ExtractionSessionService;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class ExtractionSessionServiceImpl implements ExtractionSessionService {
    private final ExtractionSessionRepository extractionSessionRepository;

    public ExtractionSessionServiceImpl(ExtractionSessionRepository extractionSessionRepository) {
        this.extractionSessionRepository = extractionSessionRepository;
    }

    @Override
    public List<ExtractionSession> findAll() {
        // TODO(student): Implement ExtractionSessionService.findAll().
        return extractionSessionRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    @Override
    public Optional<ExtractionSession> findById(Long id) {
        // TODO(student): Implement ExtractionSessionService.findById().
        return extractionSessionRepository.findById(id);
    }

    @Override
    public ExtractionSession create(ExtractionSession session) {
        // TODO(student): Implement ExtractionSessionService.create().
        return extractionSessionRepository.save(session);
    }

    @Override
    public ExtractionSession start(Long id) {
        // TODO(student): Validate the current status (only CREATED or PAUSED may
        //  start), set the status to RUNNING, stamp startedAt and save.
        ExtractionSession session = findInStatus(id, SessionStatus.CREATED, SessionStatus.PAUSED);
        session.setStatus(SessionStatus.RUNNING);
        session.setStartedAt(LocalDateTime.now());
        session.setFinishedAt(null);
        return extractionSessionRepository.save(session);
    }

    @Override
    public ExtractionSession stop(Long id) {
        // TODO(student): Implement ExtractionSessionService.stop().
        ExtractionSession session = findInStatus(id, SessionStatus.RUNNING);
        session.setStatus(SessionStatus.PAUSED);
        return extractionSessionRepository.save(session);
    }

    @Override
    public ExtractionSession complete(Long id) {
        // TODO(student): Implement ExtractionSessionService.complete().
        ExtractionSession session = findInStatus(id, SessionStatus.RUNNING);
        session.setStatus(SessionStatus.COMPLETED);
        session.setFinishedAt(LocalDateTime.now());
        return extractionSessionRepository.save(session);
    }

    @Override
    public ExtractionSession fail(Long id) {
        // TODO(student): Implement ExtractionSessionService.fail().
        ExtractionSession session = extractionSessionRepository
            .findById(id)
            .orElseThrow(() -> new SessionNotFoundException(id));
        session.setStatus(SessionStatus.FAILED);
        session.setFinishedAt(LocalDateTime.now());
        return extractionSessionRepository.save(session);
    }

    private ExtractionSession findInStatus(Long id, SessionStatus... allowedStatuses) {
        ExtractionSession session = extractionSessionRepository
            .findById(id)
            .orElseThrow(() -> new SessionNotFoundException(id));
        if (!List.of(allowedStatuses).contains(session.getStatus())) {
            throw new InvalidSessionStateException(id, session.getStatus());
        }
        return session;
    }
}
