package mk.ukim.finki.aibotbackend.integration.vezilka;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import mk.ukim.finki.aibotbackend.model.enums.DonationStatus;
import mk.ukim.finki.aibotbackend.model.exception.VezilkaIntegrationException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * {@link BatchVezilkaClient} that uses the Vezilka Public Donation API v1 with an API key.
 *
 * <p>Vezilka decides about every text right away. Accepted texts get an id that can be read back later,
 * but rejected and duplicate texts come back without one, so for those the reference stores the decision
 * itself ({@code rejected:<reason>} or {@code duplicate}). A batch gets a reference with the counts,
 * e.g. {@code batch:accepted=8,duplicate=1,rejected=2}. {@link #checkStatus(String)} understands all of them.</p>
 */
@Slf4j
@Component
public class PublicApiVezilkaClient implements BatchVezilkaClient {
    static final String DUPLICATE_REFERENCE = "duplicate";
    static final String REJECTED_REFERENCE_PREFIX = "rejected:";
    static final String BATCH_REFERENCE_PREFIX = "batch:";

    private static final int MAX_ITEMS_PER_REQUEST = 100;
    private static final int MAX_ATTEMPTS = 3;
    private static final Pattern BATCH_REFERENCE = Pattern.compile("batch:accepted=(\\d+),duplicate=(\\d+),rejected=(\\d+)");

    private final RestClient restClient;

    public PublicApiVezilkaClient(RestClient.Builder restClientBuilder, VezilkaProperties vezilkaProperties) {
        this.restClient = restClientBuilder
            .baseUrl(vezilkaProperties.baseUrl())
            .defaultHeader("X-Donation-Api-Key", vezilkaProperties.apiKey())
            .build();
    }

    @Override
    public DonationReceipt submitTextDonation(TextDonationRequest request) {
        DonationReceipt receipt = receiptOf(donate(List.of(request)).getFirst());
        log.info("Donation \"{}\" submitted to Vezilka: {}", request.title(), receipt.message());
        return receipt;
    }

    @Override
    public DonationReceipt submitTextDonations(List<TextDonationRequest> requests) {
        if (requests.isEmpty()) {
            throw new IllegalArgumentException("There is nothing to donate.");
        }

        int accepted = 0;
        int duplicates = 0;
        int rejected = 0;
        Map<String, Integer> rejectionReasons = new TreeMap<>();
        for (int from = 0; from < requests.size(); from += MAX_ITEMS_PER_REQUEST) {
            List<TextDonationRequest> chunk = requests.subList(from, Math.min(from + MAX_ITEMS_PER_REQUEST, requests.size()));
            for (JsonNode result : donate(chunk)) {
                if (result.path("deduped").asBoolean(false)) {
                    duplicates++;
                } else if ("accepted".equals(result.path("status").asText())) {
                    accepted++;
                } else {
                    rejected++;
                    rejectionReasons.merge(result.path("rejectionReason").asText("unknown"), 1, Integer::sum);
                }
            }
        }

        String reference = "%saccepted=%d,duplicate=%d,rejected=%d"
            .formatted(BATCH_REFERENCE_PREFIX, accepted, duplicates, rejected);
        String message = "%d accepted, %d already in the corpus, %d rejected".formatted(accepted, duplicates, rejected);
        if (!rejectionReasons.isEmpty()) {
            message += rejectionReasons.entrySet()
                .stream()
                .map(reason -> describe(reason.getKey()) + ": " + reason.getValue())
                .collect(Collectors.joining(", ", " (", ")"));
        }
        log.info("{} texts submitted to Vezilka: {}.", requests.size(), message);
        return new DonationReceipt(reference, message + ".");
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
        Matcher batch = BATCH_REFERENCE.matcher(vezilkaReference);
        if (batch.matches()) {
            // the batch counts as accepted when at least one of its texts is in the corpus
            int inCorpus = Integer.parseInt(batch.group(1)) + Integer.parseInt(batch.group(2));
            return inCorpus > 0 ? DonationStatus.ACCEPTED : DonationStatus.REJECTED;
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

    /**
     * Sends one request with the given texts as items and returns Vezilka's result for each of them, in order.
     */
    private List<JsonNode> donate(List<TextDonationRequest> requests) {
        String retrievedAt = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS).toString();
        List<Map<String, Object>> items = new ArrayList<>();
        for (TextDonationRequest request : requests) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("source_url", request.sourceUrl());
            item.put("text", request.content());
            item.put("page_title", request.title());
            item.put("page_language", "mk");
            item.put("retrieved_at", retrievedAt);
            items.add(item);
        }

        JsonNode response = send(() -> restClient.post()
            .uri("/api/public/v1/donations/text/")
            .body(Map.of("items", items))
            .retrieve()
            .body(JsonNode.class));

        JsonNode results = response == null ? null : response.path("results");
        if (results == null || !results.isArray() || results.size() != requests.size()) {
            throw new VezilkaIntegrationException("Vezilka did not return a result for every donated text.");
        }
        List<JsonNode> resultList = new ArrayList<>();
        results.forEach(resultList::add);
        return resultList;
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
