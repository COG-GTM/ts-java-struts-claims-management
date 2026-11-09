package com.northstar.claims.domain;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Transfer object used by the claims application when moving data between
 * the JDBC layer, service layer, and Struts presentation layer.
 */
public class ClaimNote {

    private static final Log log = LogFactory.getLog(ClaimNote.class);

    private int noteId;
    private int claimId;
    private String author;
    private String noteDate;
    private String noteText;

    /** Returns the noteId value. */
    public int getNoteId() {
        return noteId;
    }

    /** Stores the noteId value supplied by the caller. */
    public void setNoteId(int noteId) {
        this.noteId = noteId;
    }

    /** Returns the claimId value. */
    public int getClaimId() {
        return claimId;
    }

    /** Stores the claimId value supplied by the caller. */
    public void setClaimId(int claimId) {
        this.claimId = claimId;
    }

    /** Returns the author value. */
    public String getAuthor() {
        return author;
    }

    /** Stores the author value supplied by the caller. */
    public void setAuthor(String author) {
        this.author = author;
    }

    /** Returns the noteDate value. */
    public String getNoteDate() {
        return noteDate;
    }

    /** Stores the noteDate value supplied by the caller. */
    public void setNoteDate(String noteDate) {
        this.noteDate = noteDate;
    }

    /** Returns the noteText value. */
    public String getNoteText() {
        return noteText;
    }

    /** Stores the noteText value supplied by the caller. */
    public void setNoteText(String noteText) {
        this.noteText = noteText;
    }

    /** Provides a compact description useful in old server logs. */
    public String toString() {
        StringBuffer text = new StringBuffer();
        text.append("ClaimNote[");
        text.append("noteId=").append(noteId);        text.append(", ");
        text.append("claimId=").append(claimId);        text.append(", ");
        text.append("author=").append(author);        text.append(", ");
        text.append("noteDate=").append(noteDate);        text.append(", ");
        text.append("noteText=").append(noteText);
        text.append("]");
        return text.toString();
    }

    /** Writes a trace line used by the claims support desk. */
    public void logSummary() {
        log.debug(toString());
        System.out.println(toString());
    }
}
