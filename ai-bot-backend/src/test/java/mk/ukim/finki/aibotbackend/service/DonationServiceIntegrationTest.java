package mk.ukim.finki.aibotbackend.service;

import jakarta.transaction.Transactional;
import java.util.Arrays;
import java.util.List;
import mk.ukim.finki.aibotbackend.integration.vezilka.BatchVezilkaClient;
import mk.ukim.finki.aibotbackend.integration.vezilka.DonationReceipt;
import mk.ukim.finki.aibotbackend.model.domain.DonationBatch;
import mk.ukim.finki.aibotbackend.model.domain.ExtractedPost;
import mk.ukim.finki.aibotbackend.model.domain.ExtractionSession;
import mk.ukim.finki.aibotbackend.model.enums.DonationStatus;
import mk.ukim.finki.aibotbackend.model.enums.SocialNetwork;
import mk.ukim.finki.aibotbackend.model.exception.InvalidDonationStateException;
import mk.ukim.finki.aibotbackend.model.exception.PostNotDonatableException;
import mk.ukim.finki.aibotbackend.model.exception.VezilkaIntegrationException;
import mk.ukim.finki.aibotbackend.service.domain.DonationService;
import mk.ukim.finki.aibotbackend.service.domain.ExtractedPostService;
import mk.ukim.finki.aibotbackend.service.domain.ExtractionSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * TODO(student): Integration-test the donation workflow (createBatch ->
 * approve -> submit) with the full Spring context and a mocked/stubbed
 * VezilkaClient. Remove @Disabled once implemented.
 */
@SpringBootTest
@Testcontainers
@Transactional
public class DonationServiceIntegrationTest {
    private static final String REFERENCE = "batch:accepted=2,duplicate=0,rejected=0";

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

    // nothing is sent to the real doniraj.vezilka.ai from the tests
    @MockitoBean
    private BatchVezilkaClient vezilkaClient;

    @Autowired
    private DonationService donationService;

    @Autowired
    private ExtractionSessionService extractionSessionService;

    @Autowired
    private ExtractedPostService extractedPostService;

    private ExtractionSession session;

    @BeforeEach
    void setUp() {
        session = extractionSessionService.create(new ExtractionSession(SocialNetwork.YOUTUBE, "Тест сесија"));
    }

    @Test
    void testDonationWorkflow() {
        // TODO(student)
        List<Long> postIds = savePosts(
            "Денеска во Скопје ќе има сончево време и пријатна температура.",
            "Ова е уште една македонска објава што треба да се донира."
        );
        when(vezilkaClient.submitTextDonations(anyList())).thenReturn(new DonationReceipt(REFERENCE, "2 accepted."));
        when(vezilkaClient.checkStatus(REFERENCE)).thenReturn(DonationStatus.ACCEPTED);

        DonationBatch batch = donationService.createBatch(postIds);
        assertThat(batch.getStatus()).isEqualTo(DonationStatus.DRAFT);
        assertThat(batch.getPosts()).hasSize(2);

        assertThat(donationService.approve(batch.getId()).getStatus()).isEqualTo(DonationStatus.APPROVED);

        DonationBatch submitted = donationService.submit(batch.getId());
        assertThat(submitted.getStatus()).isEqualTo(DonationStatus.SUBMITTED);
        assertThat(submitted.getVezilkaReference()).isEqualTo(REFERENCE);
        assertThat(submitted.getSubmittedAt()).isNotNull();
        // every post is donated as a separate text
        verify(vezilkaClient).submitTextDonations(argThat(requests -> requests.size() == 2));

        donationService.refreshSubmittedStatuses();
        assertThat(donationService.findById(batch.getId()).orElseThrow().getStatus()).isEqualTo(DonationStatus.ACCEPTED);
    }

    @Test
    void testBatchCannotBeSubmittedBeforeItIsApproved() {
        DonationBatch batch = donationService.createBatch(savePosts("Денеска во Скопје ќе има сончево време."));

        assertThatThrownBy(() -> donationService.submit(batch.getId()))
            .isInstanceOf(InvalidDonationStateException.class);
        verifyNoInteractions(vezilkaClient);
    }

    @Test
    void testPostThatVezilkaWouldRejectCannotBeDonated() {
        List<Long> tooShort = savePosts("Кратко.");

        assertThatThrownBy(() -> donationService.createBatch(tooShort))
            .isInstanceOf(PostNotDonatableException.class);
    }

    @Test
    void testPostCannotBeInTwoBatches() {
        List<Long> postIds = savePosts("Денеска во Скопје ќе има сончево време.");
        donationService.createBatch(postIds);

        assertThatThrownBy(() -> donationService.createBatch(postIds))
            .isInstanceOf(PostNotDonatableException.class);
    }

    @Test
    void testFailedSubmissionCanBeRepeated() {
        DonationBatch batch = donationService.createBatch(savePosts("Денеска во Скопје ќе има сончево време."));
        donationService.approve(batch.getId());
        when(vezilkaClient.submitTextDonations(anyList()))
            .thenThrow(new VezilkaIntegrationException("Vezilka is not reachable."))
            .thenReturn(new DonationReceipt("batch:accepted=1,duplicate=0,rejected=0", "1 accepted."));

        assertThatThrownBy(() -> donationService.submit(batch.getId()))
            .isInstanceOf(VezilkaIntegrationException.class);
        assertThat(donationService.findById(batch.getId()).orElseThrow().getStatus()).isEqualTo(DonationStatus.FAILED);

        assertThat(donationService.submit(batch.getId()).getStatus()).isEqualTo(DonationStatus.SUBMITTED);
    }

    private List<Long> savePosts(String... contents) {
        List<ExtractedPost> posts = Arrays.stream(contents)
            .map(content -> new ExtractedPost(
                session, null, "@avtor", content, "https://www.youtube.com/watch?v=test", null, 0.9
            ))
            .toList();
        return extractedPostService
            .saveAll(posts)
            .stream()
            .map(ExtractedPost::getId)
            .toList();
    }
}
