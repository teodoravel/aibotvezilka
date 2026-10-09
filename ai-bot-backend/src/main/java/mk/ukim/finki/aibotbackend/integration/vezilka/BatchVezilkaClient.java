package mk.ukim.finki.aibotbackend.integration.vezilka;

import java.util.List;

/**
 * A {@link VezilkaClient} that can also donate many texts at once, the way the Public Donation API
 * recommends: every post is a separate item with its own source URL, up to 100 items per request.
 */
public interface BatchVezilkaClient extends VezilkaClient {
    /**
     * Submits the texts as separate items and returns one receipt for all of them.
     * Its reference can be passed to {@link #checkStatus(String)} like any other reference.
     */
    DonationReceipt submitTextDonations(List<TextDonationRequest> requests);
}
