package com.northstar.claims.service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import com.northstar.claims.repository.PolicyDAO;
import com.northstar.claims.domain.Policy;

/** Supplies policies to search, view, and underwriting pages. */
@org.springframework.stereotype.Service
public class PolicyManager {

    private final PolicyDAO dao;
    private final Log log = LogFactory.getLog(PolicyManager.class);

    public PolicyManager(PolicyDAO dao) {
        this.dao = dao;
    }


    public Policy getPolicy(int id) throws SQLException {
        log.debug("Policy lookup " + id);
        return dao.findById(id);
    }

    public List getPolicies() throws SQLException {
        return dao.findAll();
    }

    public List findByLine(String line) throws SQLException {
        return dao.findByLine(line);
    }

    public List search(String term) throws SQLException {
        return dao.search(term);
    }

    public void updateStatus(int id, String status) throws SQLException {
        dao.updateStatus(id, status);
    }

    public List emptyPolicies() {
        return new ArrayList();
    }
}
