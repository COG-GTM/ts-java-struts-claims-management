package com.northstar.claims.tests.transcript;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Workbench transcripts; tagged area until the workbench stream merges. */
@Tag("area")
class WorkbenchTranscriptTest extends TranscriptReplayTest {

    @Test
    void assignAdjuster() throws Exception {
        replay("workbench_assign");
    }

    @Test
    void statusFromQueryString() throws Exception {
        replay("workbench_status");
    }

    @Test
    void changeReserve() throws Exception {
        replay("workbench_reserve");
    }
}
