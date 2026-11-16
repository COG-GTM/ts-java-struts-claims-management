package com.northstar.claims;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import com.northstar.claims.util.ClaimImportService;

/** Verifies the delimited FNOL import used by batch intake jobs. */
public class ClaimImportServiceTest {

    private final ClaimImportService service = new ClaimImportService();

    @Test
    public void parseMapsAllFourColumns() {
        Map values = service.parse("Pat Lee|03/15/2019|Burst pipe|WATER");
        assertEquals("Pat Lee", values.get("claimant"));
        assertEquals("03/15/2019", values.get("lossDate"));
        assertEquals("Burst pipe", values.get("description"));
        assertEquals("WATER", values.get("lossType"));
        assertEquals(4, values.size());
    }

    @Test
    public void parseKeepsEmptyColumnsAndIgnoresExtras() {
        Map values = service.parse("Pat Lee||Hail|HAIL|extra");
        assertEquals("", values.get("lossDate"));
        assertEquals("HAIL", values.get("lossType"));
        assertEquals(4, values.size());
    }

    @Test
    public void parseHandlesShortAndNullLines() {
        Map shortLine = service.parse("Pat Lee");
        assertEquals("Pat Lee", shortLine.get("claimant"));
        assertNull(shortLine.get("lossDate"));
        assertEquals(1, shortLine.size());
        assertTrue(service.parse(null).isEmpty());
    }

    @Test
    public void importLineAcceptsCompleteRecord() {
        service.importLine("Pat Lee|03/15/2019|Burst pipe|WATER");
        assertEquals(1, service.acceptedCount());
        assertEquals(0, service.rejectedCount());
        assertFalse(service.hasErrors());
        Map accepted = (Map) service.accepted().get(0);
        assertEquals("Burst pipe", accepted.get("description"));
    }

    @Test
    public void importLineRejectsMissingRequiredFields() {
        service.importLine("Pat Lee|03/15/2019");
        service.importLine("|03/15/2019|Hail");
        service.importLine("Pat Lee||Hail");
        assertEquals(0, service.acceptedCount());
        assertEquals(3, service.rejectedCount());
        assertTrue(service.hasErrors());
        assertEquals("Pat Lee|03/15/2019", service.rejected().get(0));
    }

    @Test
    public void importLineRejectsNullLine() {
        service.importLine(null);
        assertEquals(1, service.rejectedCount());
        assertNull(service.rejected().get(0));
    }

    @Test
    public void validationStateDoesNotLeakBetweenLines() {
        service.importLine("Pat Lee|03/15/2019");
        service.importLine("Sam Ng|04/01/2019|Rear-ended");
        assertEquals(1, service.acceptedCount());
        assertEquals(1, service.rejectedCount());
    }

    @Test
    public void listsAreDefensiveCopies() {
        service.importLine("Sam Ng|04/01/2019|Rear-ended");
        service.importLine("bad");
        List accepted = service.accepted();
        List rejected = service.rejected();
        accepted.clear();
        rejected.clear();
        assertEquals(1, service.acceptedCount());
        assertEquals(1, service.rejectedCount());
    }

    @Test
    public void clearAndReportSummarizeCounts() {
        service.importLine("Sam Ng|04/01/2019|Rear-ended");
        service.importLine("bad");
        assertEquals("accepted=1, rejected=1", service.report());
        service.clear();
        assertEquals("accepted=0, rejected=0", service.report());
        assertFalse(service.hasErrors());
    }
}
