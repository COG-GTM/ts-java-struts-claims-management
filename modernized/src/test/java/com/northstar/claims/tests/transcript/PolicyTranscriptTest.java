package com.northstar.claims.tests.transcript;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Policy area transcripts; tagged area (run with -Pareas) until the policy stream merges. */
@Tag("area")
class PolicyTranscriptTest extends TranscriptReplayTest {

    @Test
    void policySearchByLineOfBusiness() throws Exception {
        replay("policy_search");
    }

    @Test
    void viewLowLimitPolicy() throws Exception {
        replay("policy_view");
    }
}
