package com.northstar.claims.web.policy;

/** Request strings stay untrimmed, as in the Struts form. */
public record PolicySearchForm(String lineOfBusiness) {}
