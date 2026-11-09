package com.northstar.claims.web.intake;

/** Request strings are left untrimmed, just as in the Struts IntakeForm. */
public record IntakeForm(String claimantName, String lossDate, String description) {
}
