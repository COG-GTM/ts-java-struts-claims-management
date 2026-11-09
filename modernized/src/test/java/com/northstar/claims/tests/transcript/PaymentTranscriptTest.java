package com.northstar.claims.tests.transcript;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Payment transcripts (deterministic payment 61 / CHK-61); tagged area until the payment stream merges. */
@Tag("area")
class PaymentTranscriptTest extends TranscriptReplayTest {

    @Test
    void issuePayment61() throws Exception {
        replay("payment_issue");
    }

    @Test
    void historyWithoutClaimId() throws Exception {
        replay("payment_history");
    }
}
