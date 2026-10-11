package mk.ukim.finki.aibotbackend.repository;

import jakarta.transaction.Transactional;
import java.util.Optional;
import mk.ukim.finki.aibotbackend.config.JpaConfig;
import mk.ukim.finki.aibotbackend.model.domain.ExtractionSession;
import mk.ukim.finki.aibotbackend.model.domain.ExtractionTarget;
import mk.ukim.finki.aibotbackend.model.enums.SessionStatus;
import mk.ukim.finki.aibotbackend.model.enums.SocialNetwork;
import mk.ukim.finki.aibotbackend.model.enums.TargetType;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TODO(student): Test your session queries here, following the pattern from
 * {@link UserRepositoryTest}. Remove @Disabled once implemented.
 */
@DataJpaTest
@Import(JpaConfig.class)
@Transactional
@Testcontainers
public class ExtractionSessionRepositoryTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("aibot_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private ExtractionSessionRepository extractionSessionRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Long sessionId;

    @BeforeEach
    void setUp() {
        ExtractionSession session = new ExtractionSession(SocialNetwork.YOUTUBE, "Македонски канали");
        session.getTargets().add(new ExtractionTarget(TargetType.PROFILE, "@kanal", session));
        session.getTargets().add(new ExtractionTarget(TargetType.HASHTAG, "#македонија", session));
        sessionId = extractionSessionRepository.save(session).getId();

        // so that the tests read the session back from the database
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void testFindSessions() {
        // TODO(student)
        Optional<ExtractionSession> result = extractionSessionRepository.findById(sessionId);

        assertThat(result).isPresent();
        assertThat(result.get().getSocialNetwork()).isEqualTo(SocialNetwork.YOUTUBE);
        assertThat(result.get().getStatus()).isEqualTo(SessionStatus.CREATED);
        assertThat(result.get().getDescription()).isEqualTo("Македонски канали");
        assertThat(result.get().getTargets())
            .extracting(ExtractionTarget::getValue)
            .containsExactlyInAnyOrder("@kanal", "#македонија");
    }

    @Test
    void testTargetsAreLoadedTogetherWithTheSession() {
        ExtractionSession session = extractionSessionRepository.findById(sessionId).orElseThrow();

        // the bot reads the targets on a background thread, when the session is no longer attached
        entityManager.clear();

        assertThat(Hibernate.isInitialized(session.getTargets())).isTrue();
        assertThat(session.getTargets()).hasSize(2);
    }

    @Test
    void testMissingSessionIsNotFound() {
        assertThat(extractionSessionRepository.findById(sessionId + 1000)).isEmpty();
    }
}
