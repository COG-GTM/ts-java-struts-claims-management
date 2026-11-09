package com.northstar.claims.tests.transcript;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Report transcripts (as-of 2019-04-01); tagged area until the reports stream merges. */
@Tag("area")
class ReportsTranscriptTest extends TranscriptReplayTest {

    @Test
    void openClaimsByAdjuster() throws Exception {
        replay("report_open_by_adjuster");
    }

    @Test
    void lossRatioByLine() throws Exception {
        replay("report_loss_ratio");
    }

    @Test
    void agedClaimBuckets() throws Exception {
        replay("report_aged_claims");
    }
}
