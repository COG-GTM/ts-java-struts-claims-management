package com.northstar.claims;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import com.northstar.claims.util.ClaimImportService;

/** Verifies parsing and acceptance rules for delimited intake records. */
public class ClaimImportServiceTest {

    private final ClaimImportService service = new ClaimImportService();

    @Test
    public void parseSplitsAllFourPipeDelimitedFields() {
        Map values = service.parse("Pat Lee|03/15/2019|Kitchen fire|FIRE");
        assertEquals(4, values.size());
        assertEquals("Pat Lee", values.get("claimant"));
        assertEquals("03/15/2019", values.get("lossDate"));
        assertEquals("Kitchen fire", values.get("description"));
        assertEquals("FIRE", values.get("lossType"));
    }

    @Test
    public void parseKeepsTrailingEmptyFields() {
        Map values = service.parse("Pat Lee||");
        assertEquals(3, values.size());
        assertEquals("", values.get("lossDate"));
        assertEquals("", values.get("description"));
        assertNull(values.get("lossType"));
    }

    @Test
    public void parseOfShortAndNullLines() {
        Map single = service.parse("Pat Lee");
        assertEquals(1, single.size());
        assertEquals("Pat Lee", single.get("claimant"));
        assertTrue(service.parse(null).isEmpty());
    }

    @Test
    public void parseIgnoresFieldsBeyondLossType() {
        Map values = service.parse("a|b|c|d|extra");
        assertEquals(4, values.size());
        assertEquals("d", values.get("lossType"));
    }

    @Test
    public void completeLineIsAccepted() {
        service.importLine("Pat Lee|03/15/2019|Kitchen fire");
        assertEquals(1, service.acceptedCount());
        assertEquals(0, service.rejectedCount());
        assertFalse(service.hasErrors());
        Map accepted = (Map) service.accepted().get(0);
        assertEquals("Kitchen fire", accepted.get("description"));
    }

    @Test
    public void linesMissingRequiredFieldsAreRejectedVerbatim() {
        service.importLine("|03/15/2019|Kitchen fire");
        service.importLine("Pat Lee||Kitchen fire");
        service.importLine("Pat Lee|03/15/2019|   ");
        service.importLine("Pat Lee");
        assertEquals(0, service.acceptedCount());
        assertEquals(4, service.rejectedCount());
        assertTrue(service.hasErrors());
        assertEquals("Pat Lee||Kitchen fire", service.rejected().get(1));
    }

    @Test
    public void validatorStateIsResetBetweenLines() {
        service.importLine("|03/15/2019|bad");
        service.importLine("Pat Lee|03/15/2019|good");
        assertEquals(1, service.acceptedCount());
        assertEquals(1, service.rejectedCount());
    }

    @Test
    public void nullLineIsRejected() {
        service.importLine(null);
        assertEquals(1, service.rejectedCount());
        assertNull(service.rejected().get(0));
    }

    @Test
    public void listsAreDefensiveCopiesAndClearResetsCounts() {
        service.importLine("Pat Lee|03/15/2019|ok");
        service.importLine("|x|y");
        List accepted = service.accepted();
        accepted.clear();
        assertEquals(1, service.acceptedCount());
        service.clear();
        assertEquals(0, service.acceptedCount());
        assertEquals(0, service.rejectedCount());
        assertFalse(service.hasErrors());
    }

    @Test
    public void reportSummarisesCounts() {
        service.importLine("Pat Lee|03/15/2019|ok");
        service.importLine("Kim Ng|03/16/2019|ok");
        service.importLine("||");
        assertEquals("accepted=2, rejected=1", service.report());
    }
}
