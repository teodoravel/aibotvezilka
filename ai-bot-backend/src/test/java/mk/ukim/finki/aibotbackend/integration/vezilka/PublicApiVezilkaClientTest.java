package mk.ukim.finki.aibotbackend.integration.vezilka;

import mk.ukim.finki.aibotbackend.model.enums.DonationStatus;
import mk.ukim.finki.aibotbackend.model.exception.VezilkaIntegrationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Tests the Vezilka client against a mocked API, the responses follow the Public Donation API v1 documentation.
 */
public class PublicApiVezilkaClientTest {
    private static final String TEXT_URL = "https://vezilka.test/api/public/v1/donations/text/";
    private static final TextDonationRequest REQUEST = new TextDonationRequest(
        "Македонски коментари од YouTube",
        "Ова е долг македонски текст што бил најден во коментарите на едно видео.",
        "https://www.youtube.com/watch?v=abc"
    );

    private MockRestServiceServer server;
    private PublicApiVezilkaClient vezilkaClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        vezilkaClient = new PublicApiVezilkaClient(builder, new VezilkaProperties("https://vezilka.test", "test-key"));
    }

    @Test
    void testAcceptedDonationReturnsTheVezilkaId() {
        server.expect(requestTo(TEXT_URL))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("X-Donation-Api-Key", "test-key"))
            .andExpect(jsonPath("$.items[0].source_url").value("https://www.youtube.com/watch?v=abc"))
            .andExpect(jsonPath("$.items[0].text").value(REQUEST.content()))
            .andExpect(jsonPath("$.items[0].page_title").value(REQUEST.title()))
            .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(result(
                "\"id\": \"9f2c1e8a\", \"status\": \"accepted\", \"deduped\": false, \"rejectionReason\": null")));

        DonationReceipt receipt = vezilkaClient.submitTextDonation(REQUEST);

        assertThat(receipt.reference()).isEqualTo("9f2c1e8a");
        assertThat(receipt.message()).contains("Accepted");
        server.verify();
    }

    @Test
    void testRejectedDonationKeepsTheReason() {
        server.expect(requestTo(TEXT_URL))
            .andRespond(withSuccess(result(
                "\"id\": null, \"status\": \"rejected\", \"deduped\": false, \"rejectionReason\": \"not_macedonian\""),
                MediaType.APPLICATION_JSON));

        DonationReceipt receipt = vezilkaClient.submitTextDonation(REQUEST);

        assertThat(receipt.reference()).isEqualTo("rejected:not_macedonian");
        assertThat(receipt.message()).contains("not recognized as Macedonian");
        assertThat(vezilkaClient.checkStatus(receipt.reference())).isEqualTo(DonationStatus.REJECTED);
    }

    @Test
    void testDuplicateDonationCountsAsAccepted() {
        server.expect(requestTo(TEXT_URL))
            .andRespond(withSuccess(result(
                "\"id\": null, \"status\": \"accepted\", \"deduped\": true, \"rejectionReason\": null"),
                MediaType.APPLICATION_JSON));

        DonationReceipt receipt = vezilkaClient.submitTextDonation(REQUEST);

        assertThat(receipt.reference()).isEqualTo("duplicate");
        assertThat(vezilkaClient.checkStatus(receipt.reference())).isEqualTo(DonationStatus.ACCEPTED);
    }

    @Test
    void testCheckStatusReadsTheDonationBack() {
        server.expect(requestTo("https://vezilka.test/api/public/v1/donations/9f2c1e8a/"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("X-Donation-Api-Key", "test-key"))
            .andRespond(withSuccess("{\"id\": \"9f2c1e8a\", \"status\": \"accepted\"}", MediaType.APPLICATION_JSON));

        assertThat(vezilkaClient.checkStatus("9f2c1e8a")).isEqualTo(DonationStatus.ACCEPTED);
        server.verify();
    }

    @Test
    void testWrongApiKeyThrows() {
        server.expect(requestTo(TEXT_URL))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"detail\": \"Authentication credentials were not provided.\"}"));

        assertThatThrownBy(() -> vezilkaClient.submitTextDonation(REQUEST))
            .isInstanceOf(VezilkaIntegrationException.class)
            .hasMessageContaining("API key");
    }

    @Test
    void testRateLimitIsRetried() {
        HttpHeaders retryAfter = new HttpHeaders();
        retryAfter.add("Retry-After", "0");
        server.expect(requestTo(TEXT_URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).headers(retryAfter));
        server.expect(requestTo(TEXT_URL))
            .andRespond(withSuccess(result(
                "\"id\": \"9f2c1e8a\", \"status\": \"accepted\", \"deduped\": false, \"rejectionReason\": null"),
                MediaType.APPLICATION_JSON));

        assertThat(vezilkaClient.submitTextDonation(REQUEST).reference()).isEqualTo("9f2c1e8a");
        server.verify();
    }

    private static String result(String fields) {
        return "{\"results\": [{" + fields + ", \"contentType\": \"text\", \"awardedPoints\": 0}], "
            + "\"total\": 1, \"accepted\": 0, \"rejected\": 0, \"duplicates\": 0}";
    }
}
