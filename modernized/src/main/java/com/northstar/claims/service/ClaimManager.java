package com.northstar.claims.service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import com.northstar.claims.repository.ClaimDAO;
import com.northstar.claims.domain.Claim;

/** Coordinates common claim lookups used by the workbench screens. */
@org.springframework.stereotype.Service
public class ClaimManager {

    private final ClaimDAO dao;
    private final Log log = LogFactory.getLog(ClaimManager.class);

    public ClaimManager(ClaimDAO dao) {
        this.dao = dao;
    }


    public Claim getClaim(int id) throws SQLException {
        log.info("Loading claim " + id);
        System.out.println("claim lookup: " + id);
        return dao.findById(id);
    }

    public List getOpenClaims() throws SQLException {
        return dao.findByStatus("OPEN");
    }

    public List findByStatus(String status) throws SQLException {
        return dao.findByStatus(status);
    }

    public List search(String term, String status) throws SQLException {
        return dao.search(term, status);
    }

    public void assign(int claimId, String adjuster) throws SQLException {
        dao.assign(claimId, adjuster);
    }

    public void changeReserve(int claimId, double amount) throws SQLException {
        dao.updateReserve(claimId, amount);
    }

    public void changeStatus(int claimId, String status) throws SQLException {
        dao.updateStatus(claimId, status);
    }

    public List emptyList() {
        return new ArrayList();
    }
}
