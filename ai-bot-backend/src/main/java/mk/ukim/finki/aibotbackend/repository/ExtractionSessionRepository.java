package mk.ukim.finki.aibotbackend.repository;

import java.util.Optional;
import mk.ukim.finki.aibotbackend.model.domain.ExtractionSession;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExtractionSessionRepository extends JpaRepository<ExtractionSession, Long> {
    // the bot runs a session on a background thread, outside a transaction,
    // so the targets are loaded together with the session
    @Override
    @EntityGraph(attributePaths = "targets")
    Optional<ExtractionSession> findById(Long id);
}
