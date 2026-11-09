package com.northstar.claims.tests.transcript;

import org.junit.jupiter.api.Test;

/** Foundation (Wave 0) login transcript; untagged because it must pass before any area merges. */
class FoundationTranscriptTest extends TranscriptReplayTest {

    @Test
    void login() throws Exception {
        replay("login");
    }
}
