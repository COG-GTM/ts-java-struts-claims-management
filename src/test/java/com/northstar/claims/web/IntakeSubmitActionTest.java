package com.northstar.claims.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.northstar.claims.TestDatabase;
import com.northstar.claims.dao.ClaimDAO;
import com.northstar.claims.model.Claim;
import java.util.Arrays;
import java.util.List;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/** Covers intake validation and the claim row written for a valid submission. */
public class IntakeSubmitActionTest {

    private final ActionMapping mapping = ServletStubs.mapping(new String[] { "confirm" });

    @BeforeClass
    public static void seedDatabase() throws Exception {
        TestDatabase.reset();
    }

    @AfterClass
    public static void releaseDatabase() throws Exception {
        TestDatabase.release();
    }

    private ActionForward submit(ServletStubs.Request request) throws Exception {
        return new IntakeSubmitAction().execute(mapping, null, request.asRequest(),
                new ServletStubs.Response().asResponse());
    }

    private void assertValidationErrors(ServletStubs.Request request, String[] expected)
            throws Exception {
        int before = new ClaimDAO().count();
        ActionForward forward = submit(request);
        assertEquals("/WEB-INF/jsp/intake/new.jsp", forward.getPath());
        assertEquals(Arrays.asList(expected), request.attributes.get("nsValidationErrors"));
        assertNull(request.attributes.get("claimId"));
        assertEquals(before, new ClaimDAO().count());
    }

    @Test
    public void emptySubmissionReportsClaimantAndDescription() throws Exception {
        assertValidationErrors(new ServletStubs.Request(), new String[] {
            "errors.claimant.required", "errors.description.required" });
    }

    @Test
    public void whitespaceClaimantIsRequired() throws Exception {
        assertValidationErrors(new ServletStubs.Request()
                .param("claimantName", "   ").param("description", "Burst pipe"),
                new String[] { "errors.claimant.required" });
    }

    @Test
    public void whitespaceDescriptionIsRequired() throws Exception {
        assertValidationErrors(new ServletStubs.Request()
                .param("claimantName", "Pat Doe").param("description", " "),
                new String[] { "errors.description.required" });
    }

    @Test
    public void isoLossDateIsRejected() throws Exception {
        assertValidationErrors(new ServletStubs.Request()
                .param("claimantName", "Pat Doe").param("description", "Burst pipe")
                .param("lossDate", "2019-03-10"),
                new String[] { "errors.lossdate.format" });
    }

    @Test
    public void allErrorsAreReportedTogether() throws Exception {
        assertValidationErrors(new ServletStubs.Request().param("lossDate", "3/1/2019"),
                new String[] { "errors.claimant.required", "errors.description.required",
                    "errors.lossdate.format" });
    }

    @Test
    public void validSubmissionInsertsOpenClaim() throws Exception {
        int expectedId = new ClaimDAO().count() + 1;
        ServletStubs.Request request = new ServletStubs.Request()
                .param("claimantName", "Pat Doe").param("description", "Burst pipe")
                .param("lossDate", "03/10/2019");
        ActionForward forward = submit(request);

        assertEquals("confirm", forward.getName());
        assertEquals(String.valueOf(expectedId), request.attributes.get("claimId"));
        assertEquals("OPEN", request.attributes.get("claimStatus"));
        assertEquals("2019-03-10", request.sessionAttributes.get("lastLossDate"));

        Claim claim = new ClaimDAO().findById(expectedId);
        assertEquals("CLM-" + expectedId, claim.getClaimNumber());
        assertEquals(9001, claim.getPolicyId());
        assertEquals("Pat Doe", claim.getClaimantName());
        assertEquals("Burst pipe", claim.getDescription());
        assertEquals("2019-03-10", claim.getLossDate());
        assertEquals("2019-03-10", claim.getReportedDate());
        assertEquals("WATER", claim.getLossType());
        assertEquals("OPEN", claim.getStatus());
        assertEquals(0.0, claim.getReserveAmount(), 0.001);
        assertEquals("adjuster1", claim.getAssignedAdjuster());
        assertEquals("supervisor", claim.getCreatedBy());
    }

    @Test
    public void blankLossDateDefaultsToOperatorDate() throws Exception {
        ServletStubs.Request request = new ServletStubs.Request()
                .param("claimantName", "Lee Roe").param("description", "Hail damage")
                .param("lossDate", "");
        assertEquals("confirm", submit(request).getName());
        int claimId = Integer.parseInt((String) request.attributes.get("claimId"));
        assertEquals("2019-04-01", new ClaimDAO().findById(claimId).getLossDate());
        assertEquals("2019-04-01", request.sessionAttributes.get("lastLossDate"));
    }

    @Test
    public void consecutiveSubmissionsReceiveIncreasingIds() throws Exception {
        ServletStubs.Request first = new ServletStubs.Request()
                .param("claimantName", "A").param("description", "First");
        ServletStubs.Request second = new ServletStubs.Request()
                .param("claimantName", "B").param("description", "Second");
        submit(first);
        submit(second);
        int firstId = Integer.parseInt((String) first.attributes.get("claimId"));
        int secondId = Integer.parseInt((String) second.attributes.get("claimId"));
        assertEquals(firstId + 1, secondId);
        List byStatus = new ClaimDAO().findByStatus("OPEN");
        assertTrue(byStatus.size() >= 26);
    }
}
