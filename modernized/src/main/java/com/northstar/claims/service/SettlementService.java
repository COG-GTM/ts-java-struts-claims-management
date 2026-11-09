package com.northstar.claims.service;

import java.sql.SQLException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import com.northstar.claims.repository.SettlementDAO;
import com.northstar.claims.domain.Settlement;

/** Combines settlement calculation and persistence for the settlement screens. */
@org.springframework.stereotype.Service
public class SettlementService {

    private final SettlementDAO dao;
    private final Log log = LogFactory.getLog(SettlementService.class);

    private final SettlementCalculator calculator;

    public SettlementService(SettlementDAO dao, SettlementCalculator calculator) {
        this.dao = dao;
        this.calculator = calculator;
    }

    public Settlement calculateAndSave(int claimId, double coveredAmount,
            String deductible, double depreciation, double policyLimit)
            throws SQLException {
        Settlement value = calculator.calculate(
                coveredAmount, deductible, depreciation, policyLimit);
        value.setClaimId(claimId);
        value.setSettlementId(claimId + 10000);
        value.setCalculatedBy("supervisor");
        value.setCalculatedDate("2019-03-01");
        dao.save(value);
        log.info("Saved settlement for claim " + claimId);
        return value;
    }

    public Settlement findByClaim(int claimId) throws SQLException {
        return dao.findByClaim(claimId);
    }

    public void save(Settlement value) throws SQLException {
        dao.save(value);
    }
}
