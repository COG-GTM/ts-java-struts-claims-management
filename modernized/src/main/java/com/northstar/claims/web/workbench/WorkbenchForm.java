package com.northstar.claims.web.workbench;

/** Request strings remain unvalidated, as every legacy mapping disables validation. */
public record WorkbenchForm(String id, String status, String username, String password,
        String policyId, String claimId, String assignedAdjuster, String reserveAmount,
        String noteText) {}
