package mk.ukim.finki.aibotbackend.integration.vezilka;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import mk.ukim.finki.aibotbackend.model.enums.DonationStatus;
import mk.ukim.finki.aibotbackend.model.exception.VezilkaIntegrationException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * {@link VezilkaClient} that uses the Vezilka Public Donation API v1 with an API key.
 *
 * <p>Vezilka decides about every text right away. Accepted texts get an id that can be read back later,
 * but rejected and duplicate texts come back without one, so for those the reference stores the decision
 * itself ({@code rejected:<reason>} or {@code duplicate}) and {@link #checkStatus(String)} understands both.</p>
 */
@Slf4j
@Component
public class PublicApiVezilkaClient implements VezilkaClient {
    static final String DUPLICATE_REFERENCE = "duplicate";
    static final String REJECTED_REFERENCE_PREFIX = "rejected:";

    private static final int MAX_ATTEMPTS = 3;

    private final RestClient restClient;

    public PublicApiVezilkaClient(RestClient.Builder restClientBuilder, VezilkaProperties vezilkaProperties) {
        this.restClient = restClientBuilder
            .baseUrl(vezilkaProperties.baseUrl())
            .defaultHeader("X-Donation-Api-Key", vezilkaProperties.apiKey())
            .build();
    }

    @Override
    public DonationReceipt submitTextDonation(TextDonationRequest request) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("source_url", request.sourceUrl());
        item.put("text", request.content());
        item.put("page_title", request.title());
        item.put("page_language", "mk");
        item.put("retrieved_at", OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS).toString());

        JsonNode response = send(() -> restClient.post()
            .uri("/api/public/v1/donations/text/")
            .body(Map.of("items", List.of(item)))
            .retrieve()
            .body(JsonNode.class));

        JsonNode result = response == null ? null : response.path("results").path(0);
        if (result == null || result.isMissingNode()) {
            throw new VezilkaIntegrationException("Vezilka did not return a result for the donation.");
        }
        DonationReceipt receipt = receiptOf(result);
        log.info("Donation \"{}\" submitted to Vezilka: {}", request.title(), receipt.message());
        return receipt;
    }

    @Override
    public DonationStatus checkStatus(String vezilkaReference) {
        if (vezilkaReference == null || vezilkaReference.isBlank()) {
            throw new VezilkaIntegrationException("The donation has no Vezilka reference to check.");
        }
        if (vezilkaReference.equals(DUPLICATE_REFERENCE)) {
            return DonationStatus.ACCEPTED;
        }
        if (vezilkaReference.startsWith(REJECTED_REFERENCE_PREFIX)) {
            return DonationStatus.REJECTED;
        }

        JsonNode donation = send(() -> restClient.get()
            .uri("/api/public/v1/donations/{id}/", vezilkaReference)
            .retrieve()
            .body(JsonNode.class));
        return switch (donation == null ? "" : donation.path("status").asText()) {
            case "accepted" -> DonationStatus.ACCEPTED;
            case "rejected" -> DonationStatus.REJECTED;
            default -> DonationStatus.SUBMITTED;
        };
    }

    private DonationReceipt receiptOf(JsonNode result) {
        // a duplicate counts as accepted, the text is already in the corpus
        if (result.path("deduped").asBoolean(false)) {
            return new DonationReceipt(DUPLICATE_REFERENCE, "Already in the Vezilka corpus.");
        }
        if ("accepted".equals(result.path("status").asText()) && !result.path("id").isNull()) {
            return new DonationReceipt(result.path("id").asText(), "Accepted into the Vezilka corpus.");
        }
        String reason = result.path("rejectionReason").asText("unknown");
        return new DonationReceipt(REJECTED_REFERENCE_PREFIX + reason, "Rejected by Vezilka: " + describe(reason) + ".");
    }

    private static String describe(String rejectionReason) {
        return switch (rejectionReason) {
            case "missing_source_url" -> "the source URL is missing";
            case "text_too_short" -> "the text is shorter than 20 characters";
            case "text_too_long" -> "the text is longer than 100,000 characters";
            case "not_macedonian" -> "the text was not recognized as Macedonian";
            case "conflict" -> "a conflict while saving, it can be submitted again later";
            default -> rejectionReason;
        };
    }

    private JsonNode send(Supplier<JsonNode> call) {
        for (int attempt = 1; ; attempt++) {
            try {
                return call.get();
            } catch (HttpClientErrorException.TooManyRequests e) {
                if (attempt == MAX_ATTEMPTS) {
                    throw new VezilkaIntegrationException("Vezilka rate limit reached, try again later.", e);
                }
                long seconds = retryAfterSeconds(e);
                log.warn("Vezilka rate limit reached, trying again in {} s.", seconds);
                pause(seconds * 1000);
            } catch (HttpClientErrorException.Unauthorized e) {
                throw new VezilkaIntegrationException("Vezilka did not accept the API key, check VEZILKA_API_KEY.", e);
            } catch (RestClientException e) {
                throw new VezilkaIntegrationException("Communication with Vezilka failed: " + e.getMessage(), e);
            }
        }
    }

    private static long retryAfterSeconds(HttpClientErrorException e) {
        String retryAfter = e.getResponseHeaders() == null ? null : e.getResponseHeaders().getFirst("Retry-After");
        try {
            return retryAfter == null ? 10 : Math.min(Long.parseLong(retryAfter.strip()), 60);
        } catch (NumberFormatException ignored) {
            return 10;
        }
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
