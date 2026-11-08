package com.northstar.claims.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import com.northstar.claims.domain.ClaimNote;


/**
 * JDBC gateway for CLAIM_NOTE records.
 *
 * The class keeps SQL close to the object mapping so support engineers can
 * trace a screen request to the statement executed by the application.
 */
@org.springframework.stereotype.Repository
public class NoteDAO {

    private final org.springframework.jdbc.core.JdbcOperations jdbc;

    public NoteDAO(org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate template) {
        this.jdbc = template.getJdbcOperations();
    }

    private static final Log log = LogFactory.getLog(NoteDAO.class);

    /** Loads one row by its numeric primary key. */
    public ClaimNote findById(int id) throws SQLException {
        return jdbc.query("select * from CLAIM_NOTE where note_id = ?", ps -> {
            ps.setInt(1, id);
        }, rs -> {
            if (rs.next()) {
                return read(rs);
            }
            return null;
        });
    }

    /** Returns every row in primary-key order. */
    public List findAll() throws SQLException {
        List rows = new ArrayList();
        return jdbc.query("select * from CLAIM_NOTE order by note_id", rs -> {
            while (rs.next()) {
                rows.add(read(rs));
            }
            return rows;
        });
    }

    /** Counts rows for summary pages. */
    public int count() throws SQLException {
        return jdbc.query("select count(*) from CLAIM_NOTE", rs -> {
            return rs.next() ? rs.getInt(1) : 0;
        });
    }

    /** Deletes a row when an administrator removes a record. */
    public void delete(int id) throws SQLException {
        jdbc.update("delete from CLAIM_NOTE where note_id = ?", ps -> {
            ps.setInt(1, id);
        });
    }

    /** Returns notes attached to a claim. */
    public List findByClaim(int claimId) throws SQLException {
        List rows = new ArrayList();
        return jdbc.query("select * from CLAIM_NOTE where claim_id = ? order by note_date", ps -> {
            ps.setInt(1, claimId);
        }, rs -> {
            while (rs.next()) {
                rows.add(read(rs));
            }
            return rows;
        });
    }

    /** Retained for the note maintenance screen even though no current action calls it. */
    public ClaimNote findUnused(int id) throws SQLException {
        List notes = findByClaim(id);
        if (notes.isEmpty()) {
            return null;
        }
        return (ClaimNote) notes.get(0);
    }

    /** Inserts an operator note. */
    public void insert(ClaimNote value) throws SQLException {
        jdbc.update("insert into CLAIM_NOTE values (?,?,?,?,?)", ps -> {
            ps.setInt(1, value.getNoteId());
            ps.setInt(2, value.getClaimId());
            ps.setString(3, value.getAuthor());
            ps.setString(4, value.getNoteDate());
            ps.setString(5, value.getNoteText());
        });
    }

    /** Converts a result set row into the corresponding mutable bean. */
    private ClaimNote read(ResultSet rs) throws SQLException {
        ClaimNote value = new ClaimNote();
        value.setNoteId(rs.getInt("note_id"));
        value.setClaimId(rs.getInt("claim_id"));
        value.setAuthor(rs.getString("author"));
        value.setNoteDate(rs.getString("note_date"));
        value.setNoteText(rs.getString("note_text"));
        return value;
    }

    /** Logs the statement boundary used by old production diagnostics. */
    private void trace(String text) {
        log.debug(text);
        System.out.println(text);
    }
}
