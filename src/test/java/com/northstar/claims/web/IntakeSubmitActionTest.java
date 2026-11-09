package com.northstar.claims.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import com.northstar.claims.TestDatabase;
import com.northstar.claims.dao.ClaimDAO;
import com.northstar.claims.model.Claim;

/** Covers FNOL validation and the claim row written by a valid submission. */
public class IntakeSubmitActionTest {

    private final IntakeSubmitAction action = new IntakeSubmitAction();
    private ActionMapping mapping;

    @Before
    public void seed() throws Exception {
        TestDatabase.reset();
        mapping = new ActionMapping();
        mapping.addForwardConfig(new ActionForward("confirm",
                "/WEB-INF/jsp/intake/confirm.jsp", false));
    }

    @AfterClass
    public static void release() throws Exception {
        TestDatabase.release();
    }

    private RequestStub validRequest() {
        return new RequestStub().param("claimantName", "Jordan Fields")
                .param("description", "Kitchen pipe burst")
                .param("lossDate", "03/20/2019");
    }

    private ActionForward submit(RequestStub stub) throws Exception {
        return action.execute(mapping, null, stub.request(), null);
    }

    @Test
    public void validSubmissionInsertsOpenClaimAndForwardsToConfirm() throws Exception {
        RequestStub stub = validRequest();
        ActionForward forward = submit(stub);
        assertEquals("confirm", forward.getName());
        assertEquals("121", stub.attributes.get("claimId"));
        assertEquals("OPEN", stub.attributes.get("claimStatus"));
        assertEquals("2019-03-20", stub.sessionAttributes.get("lastLossDate"));

        Claim claim = new ClaimDAO().findById(121);
        assertEquals("CLM-121", claim.getClaimNumber());
        assertEquals(9001, claim.getPolicyId());
        assertEquals("Jordan Fields", claim.getClaimantName());
        assertEquals("Kitchen pipe burst", claim.getDescription());
        assertEquals("2019-03-20", claim.getLossDate());
        assertEquals("2019-03-20", claim.getReportedDate());
        assertEquals("WATER", claim.getLossType());
        assertEquals("OPEN", claim.getStatus());
        assertEquals(0.0, claim.getReserveAmount(), 0.0);
        assertEquals("adjuster1", claim.getAssignedAdjuster());
    }

    @Test
    public void blankLossDateUsesOperatorDefault() throws Exception {
        RequestStub stub = validRequest().param("lossDate", "");
        assertEquals("confirm", submit(stub).getName());
        assertEquals("2019-04-01", new ClaimDAO().findById(121).getLossDate());
    }

    @Test
    public void missingClaimantIsRejectedWithoutInsert() throws Exception {
        RequestStub stub = validRequest().param("claimantName", "  ");
        ActionForward forward = submit(stub);
        assertEquals("/WEB-INF/jsp/intake/new.jsp", forward.getPath());
        assertFalse(forward.getRedirect());
        assertEquals(Arrays.asList(new String[] { "errors.claimant.required" }),
                stub.attributes.get("nsValidationErrors"));
        assertEquals(120, new ClaimDAO().count());
        assertNull(stub.attributes.get("claimId"));
    }

    @Test
    public void missingDescriptionIsRejected() throws Exception {
        RequestStub stub = validRequest().param("description", null);
        submit(stub);
        assertEquals(Arrays.asList(new String[] { "errors.description.required" }),
                stub.attributes.get("nsValidationErrors"));
        assertEquals(120, new ClaimDAO().count());
    }

    @Test
    public void malformedLossDateIsRejected() throws Exception {
        RequestStub stub = validRequest().param("lossDate", "2019-03-20");
        submit(stub);
        assertEquals(Arrays.asList(new String[] { "errors.lossdate.format" }),
                stub.attributes.get("nsValidationErrors"));
        assertEquals(120, new ClaimDAO().count());
    }

    @Test
    public void allValidationErrorsAreReportedTogether() throws Exception {
        RequestStub stub = new RequestStub().param("lossDate", "3/1/19");
        submit(stub);
        assertEquals(Arrays.asList(new String[] { "errors.claimant.required",
                "errors.description.required", "errors.lossdate.format" }),
                stub.attributes.get("nsValidationErrors"));
    }

    @Test
    public void consecutiveSubmissionsReceiveSequentialIds() throws Exception {
        submit(validRequest());
        RequestStub second = validRequest().param("claimantName", "Riley Ng");
        submit(second);
        assertEquals("122", second.attributes.get("claimId"));
        assertEquals("Riley Ng", new ClaimDAO().findById(122).getClaimantName());
        assertTrue(new ClaimDAO().count() == 122);
    }
}
