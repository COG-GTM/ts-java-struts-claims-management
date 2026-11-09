package com.northstar.claims.domain;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Transfer object used by the claims application when moving data between
 * the JDBC layer, service layer, and Struts presentation layer.
 */
public class InsuredParty {

    private static final Log log = LogFactory.getLog(InsuredParty.class);

    private int partyId;
    private int policyId;
    private String name;
    private String relationship;
    private String phone;
    private String email;

    /** Returns the partyId value. */
    public int getPartyId() {
        return partyId;
    }

    /** Stores the partyId value supplied by the caller. */
    public void setPartyId(int partyId) {
        this.partyId = partyId;
    }

    /** Returns the policyId value. */
    public int getPolicyId() {
        return policyId;
    }

    /** Stores the policyId value supplied by the caller. */
    public void setPolicyId(int policyId) {
        this.policyId = policyId;
    }

    /** Returns the name value. */
    public String getName() {
        return name;
    }

    /** Stores the name value supplied by the caller. */
    public void setName(String name) {
        this.name = name;
    }

    /** Returns the relationship value. */
    public String getRelationship() {
        return relationship;
    }

    /** Stores the relationship value supplied by the caller. */
    public void setRelationship(String relationship) {
        this.relationship = relationship;
    }

    /** Returns the phone value. */
    public String getPhone() {
        return phone;
    }

    /** Stores the phone value supplied by the caller. */
    public void setPhone(String phone) {
        this.phone = phone;
    }

    /** Returns the email value. */
    public String getEmail() {
        return email;
    }

    /** Stores the email value supplied by the caller. */
    public void setEmail(String email) {
        this.email = email;
    }

    /** Provides a compact description useful in old server logs. */
    public String toString() {
        StringBuffer text = new StringBuffer();
        text.append("InsuredParty[");
        text.append("partyId=").append(partyId);        text.append(", ");
        text.append("policyId=").append(policyId);        text.append(", ");
        text.append("name=").append(name);        text.append(", ");
        text.append("relationship=").append(relationship);        text.append(", ");
        text.append("phone=").append(phone);        text.append(", ");
        text.append("email=").append(email);
        text.append("]");
        return text.toString();
    }

    /** Writes a trace line used by the claims support desk. */
    public void logSummary() {
        log.debug(toString());
        System.out.println(toString());
    }
}
