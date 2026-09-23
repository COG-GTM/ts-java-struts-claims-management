package com.northstar.claims.model;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Transfer object used by the claims application when moving data between
 * the JDBC layer, service layer, and Struts presentation layer.
 */
public class Adjuster {

    private static final Log log = LogFactory.getLog(Adjuster.class);

    private int adjusterId;
    private String username;
    private String passwordHash;
    private String fullName;
    private String region;
    private boolean active;

    /** Returns the adjusterId value. */
    public int getAdjusterId() {
        return adjusterId;
    }

    /** Stores the adjusterId value supplied by the caller. */
    public void setAdjusterId(int adjusterId) {
        this.adjusterId = adjusterId;
    }

    /** Returns the username value. */
    public String getUsername() {
        return username;
    }

    /** Stores the username value supplied by the caller. */
    public void setUsername(String username) {
        this.username = username;
    }

    /** Returns the stored PBKDF2 credential value. */
    public String getPasswordHash() {
        return passwordHash;
    }

    /** Stores the PBKDF2 credential value supplied by the caller. */
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    /** Returns the fullName value. */
    public String getFullName() {
        return fullName;
    }

    /** Stores the fullName value supplied by the caller. */
    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    /** Returns the region value. */
    public String getRegion() {
        return region;
    }

    /** Stores the region value supplied by the caller. */
    public void setRegion(String region) {
        this.region = region;
    }

    /** Returns the active value. */
    public boolean isActive() {
        return active;
    }

    /** Stores the active value supplied by the caller. */
    public void setActive(boolean active) {
        this.active = active;
    }

    /** Provides a compact description useful in old server logs. */
    public String toString() {
        StringBuffer text = new StringBuffer();
        text.append("Adjuster[");
        text.append("adjusterId=").append(adjusterId);        text.append(", ");
        text.append("username=").append(username);        text.append(", ");
        text.append("fullName=").append(fullName);        text.append(", ");
        text.append("region=").append(region);        text.append(", ");
        text.append("active=").append(active);
        text.append("]");
        return text.toString();
    }

    /** Writes a trace line used by the claims support desk. */
    public void logSummary() {
        log.debug(toString());
        System.out.println(toString());
    }
}
