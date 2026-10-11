package mk.ukim.finki.aibotbackend.service.domain.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import mk.ukim.finki.aibotbackend.events.DonationBatchSubmittedEvent;
import mk.ukim.finki.aibotbackend.integration.vezilka.BatchVezilkaClient;
import mk.ukim.finki.aibotbackend.integration.vezilka.DonationReceipt;
import mk.ukim.finki.aibotbackend.integration.vezilka.TextDonationRequest;
import mk.ukim.finki.aibotbackend.model.domain.DonationBatch;
import mk.ukim.finki.aibotbackend.model.domain.ExtractedPost;
import mk.ukim.finki.aibotbackend.model.enums.DonationStatus;
import mk.ukim.finki.aibotbackend.model.enums.SocialNetwork;
import mk.ukim.finki.aibotbackend.model.exception.DonationBatchNotFoundException;
import mk.ukim.finki.aibotbackend.model.exception.InvalidDonationStateException;
import mk.ukim.finki.aibotbackend.model.exception.PostNotDonatableException;
import mk.ukim.finki.aibotbackend.model.exception.PostNotFoundException;
import mk.ukim.finki.aibotbackend.model.exception.VezilkaIntegrationException;
import mk.ukim.finki.aibotbackend.repository.DonationBatchRepository;
import mk.ukim.finki.aibotbackend.service.domain.DonationService;
import mk.ukim.finki.aibotbackend.service.domain.ExtractedPostService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class DonationServiceImpl implements DonationService {
    // the same limits Vezilka checks, so posts it would reject anyway are not sent at all
    private static final int MIN_TEXT_LENGTH = 20;
    private static final int MAX_TEXT_LENGTH = 100_000;
    private static final double MIN_MACEDONIAN_CONFIDENCE = 0.5;

    private final DonationBatchRepository donationBatchRepository;
    private final ExtractedPostService extractedPostService;
    private final BatchVezilkaClient vezilkaClient;
    private final ApplicationEventPublisher applicationEventPublisher;

    public DonationServiceImpl(
        DonationBatchRepository donationBatchRepository,
        ExtractedPostService extractedPostService,
        BatchVezilkaClient vezilkaClient,
        ApplicationEventPublisher applicationEventPublisher
    ) {
        this.donationBatchRepository = donationBatchRepository;
        this.extractedPostService = extractedPostService;
        this.vezilkaClient = vezilkaClient;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public List<DonationBatch> findAll() {
        // TODO(student): Implement DonationService.findAll().
        return donationBatchRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    @Override
    public Optional<DonationBatch> findById(Long id) {
        // TODO(student): Implement DonationService.findById().
        return donationBatchRepository.findById(id);
    }

    @Override
    public DonationBatch createBatch(List<Long> postIds) {
        // TODO(student): Load the posts (extractedPostService.findAllById), create a
        //  DRAFT batch, attach the posts to it and save everything.
        List<Long> ids = postIds.stream().distinct().toList();
        List<ExtractedPost> posts = extractedPostService.findAllById(ids);
        Set<Long> foundIds = posts.stream().map(ExtractedPost::getId).collect(Collectors.toSet());
        ids.stream()
            .filter(id -> !foundIds.contains(id))
            .findFirst()
            .ifPresent(missingId -> {
                throw new PostNotFoundException(missingId);
            });
        posts.forEach(DonationServiceImpl::checkDonatable);

        DonationBatch batch = donationBatchRepository.save(new DonationBatch(DonationStatus.DRAFT));
        posts.forEach(post -> post.setDonationBatch(batch));
        batch.getPosts().addAll(posts);
        extractedPostService.saveAll(posts);
        return batch;
    }

    @Override
    public DonationBatch approve(Long id) {
        // TODO(student): Implement DonationService.approve().
        DonationBatch batch = findInStatus(id, DonationStatus.DRAFT);
        batch.setStatus(DonationStatus.APPROVED);
        return donationBatchRepository.save(batch);
    }

    @Override
    public DonationBatch submit(Long id) {
        // TODO(student): Build a TextDonationRequest from the batch content, call
        //  vezilkaClient.submitTextDonation, store the receipt reference, stamp
        //  submittedAt, set the status to SUBMITTED and save. Consider publishing
        //  a DonationBatchSubmittedEvent afterwards.
        // a FAILED batch can be sent again, texts that already reached Vezilka come back as duplicates
        DonationBatch batch = findInStatus(id, DonationStatus.APPROVED, DonationStatus.FAILED);
        List<TextDonationRequest> requests = batch.getPosts()
            .stream()
            .map(DonationServiceImpl::toDonationRequest)
            .toList();

        try {
            DonationReceipt receipt = vezilkaClient.submitTextDonations(requests);
            log.info("Donation batch {} submitted to Vezilka: {}", id, receipt.message());
            batch.setVezilkaReference(receipt.reference());
        } catch (VezilkaIntegrationException e) {
            batch.setStatus(DonationStatus.FAILED);
            donationBatchRepository.save(batch);
            throw e;
        }

        batch.setSubmittedAt(LocalDateTime.now());
        batch.setStatus(DonationStatus.SUBMITTED);
        DonationBatch submitted = donationBatchRepository.save(batch);
        applicationEventPublisher.publishEvent(new DonationBatchSubmittedEvent(id));
        return submitted;
    }

    @Override
    public void refreshSubmittedStatuses() {
        // TODO(student): For every batch in status SUBMITTED, call
        //  vezilkaClient.checkStatus(batch.getVezilkaReference()) and update the status.
        for (DonationBatch batch : donationBatchRepository.findAllByStatus(DonationStatus.SUBMITTED)) {
            try {
                DonationStatus status = vezilkaClient.checkStatus(batch.getVezilkaReference());
                if (status != batch.getStatus()) {
                    batch.setStatus(status);
                    donationBatchRepository.save(batch);
                    log.info("Donation batch {} is now {}.", batch.getId(), status);
                }
            } catch (VezilkaIntegrationException e) {
                // it stays SUBMITTED and is checked again on the next refresh
                log.warn("Could not check the status of donation batch {}: {}", batch.getId(), e.getMessage());
            }
        }
    }

    private DonationBatch findInStatus(Long id, DonationStatus... allowedStatuses) {
        DonationBatch batch = donationBatchRepository
            .findById(id)
            .orElseThrow(() -> new DonationBatchNotFoundException(id));
        if (!List.of(allowedStatuses).contains(batch.getStatus())) {
            throw new InvalidDonationStateException(id, batch.getStatus());
        }
        return batch;
    }

    private static void checkDonatable(ExtractedPost post) {
        String content = post.getContent() == null ? "" : post.getContent().strip();
        String reason = null;
        if (post.getDonationBatch() != null) {
            reason = "it is already in donation batch " + post.getDonationBatch().getId();
        } else if (post.getSourceUrl() == null || post.getSourceUrl().isBlank()) {
            reason = "it has no source URL";
        } else if (content.length() < MIN_TEXT_LENGTH) {
            reason = "its text is shorter than 20 characters";
        } else if (content.length() > MAX_TEXT_LENGTH) {
            reason = "its text is longer than 100,000 characters";
        } else if (post.getMacedonianConfidence() == null || post.getMacedonianConfidence() < MIN_MACEDONIAN_CONFIDENCE) {
            reason = "its text does not look Macedonian enough";
        }
        if (reason != null) {
            throw new PostNotDonatableException(post.getId(), reason);
        }
    }

    private static TextDonationRequest toDonationRequest(ExtractedPost post) {
        String networkName = displayName(post.getSession().getSocialNetwork());
        String title = post.getAuthorHandle() == null ? networkName : networkName + " - " + post.getAuthorHandle();
        return new TextDonationRequest(title, post.getContent().strip(), post.getSourceUrl());
    }

    private static String displayName(SocialNetwork socialNetwork) {
        return switch (socialNetwork) {
            case FACEBOOK -> "Facebook";
            case INSTAGRAM -> "Instagram";
            case X -> "X";
            case REDDIT -> "Reddit";
            case TIKTOK -> "TikTok";
            case YOUTUBE -> "YouTube";
            case THREADS -> "Threads";
            case LINKEDIN -> "LinkedIn";
        };
    }
}
