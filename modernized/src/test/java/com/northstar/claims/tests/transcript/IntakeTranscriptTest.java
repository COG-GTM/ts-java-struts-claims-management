package com.northstar.claims.tests.transcript;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Intake (FNOL) transcripts incl. lenient date and HTTP 200 validation forwards; tagged area until the intake stream merges. */
@Tag("area")
class IntakeTranscriptTest extends TranscriptReplayTest {

    @Test
    void fnolSubmitCreatesClaim121() throws Exception {
        replay("fnol_submit");
    }

    @Test
    void lenientFebruaryDateBecomesMarch2() throws Exception {
        replay("intake_lenient_date");
    }

    @Test
    void missingClaimantIs200ValidationForward() throws Exception {
        replay("intake_missing_claimant");
    }

    @Test
    void missingDescriptionIs200ValidationForward() throws Exception {
        replay("intake_missing_description");
    }

    @Test
    void badDateShapeIs200ValidationForward() throws Exception {
        replay("intake_bad_date");
    }
}
