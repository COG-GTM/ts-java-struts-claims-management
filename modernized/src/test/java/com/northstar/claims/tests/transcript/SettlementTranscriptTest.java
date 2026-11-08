package com.northstar.claims.tests.transcript;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Settlement transcripts pinning the calculator quirks end to end; tagged area until the settlement stream merges. */
@Tag("area")
class SettlementTranscriptTest extends TranscriptReplayTest {

    @Test
    void standardCalculationCappedAtLowLimit() throws Exception {
        replay("settlement_calculate");
    }

    @Test
    void saveCalculatedSettlement() throws Exception {
        replay("settlement_save");
    }

    @Test
    void blankDeductibleIsZeroOnClosedClaim() throws Exception {
        replay("settlement_blank_deductible");
    }

    @Test
    void halfCentRoundsDownButCoveredDisplaysUp() throws Exception {
        replay("settlement_half_cent");
    }

    @Test
    void policyCapSetsCappedAtLimit() throws Exception {
        replay("settlement_policy_cap");
    }

    @Test
    void deductibleAboveLossFloorsAtZero() throws Exception {
        replay("settlement_deductible_floor");
    }
}
