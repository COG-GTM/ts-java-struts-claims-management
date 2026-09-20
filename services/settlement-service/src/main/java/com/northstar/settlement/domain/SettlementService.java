package com.northstar.settlement.domain;

import com.northstar.settlement.persistence.ClaimRecord;
import com.northstar.settlement.persistence.ClaimRepository;
import com.northstar.settlement.persistence.PolicyRepository;
import com.northstar.settlement.persistence.SettlementRepository;
import com.northstar.settlement.persistence.SettlementRow;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The domain rules of {@code SettlementCalculateAction}, {@code SettlementSaveAction}
 * and {@code SettlementDetailAction} with the Struts plumbing removed
 * (SPEC-SETTLE-001 sections 3.2, 3.3 and 3.5).
 *
 * <p>{@code com.northstar.claims.service.SettlementService.calculateAndSave} is dead
 * code in the monolith (SETTLE-R47) and is deliberately not ported.
 */
@Service
public class SettlementService {

    /** SETTLE-R41: every save stamps this fixed date, whatever the real date is. */
    static final LocalDate LEGACY_CALCULATED_DATE = LocalDate.of(2019, 4, 1);

    private final ClaimRepository claims;
    private final PolicyRepository policies;
    private final SettlementRepository settlements;
    private final String operator;

    public SettlementService(ClaimRepository claims, PolicyRepository policies,
            SettlementRepository settlements,
            @Value("${settlement.operator:supervisor}") String operator) {
        this.claims = claims;
        this.policies = policies;
        this.settlements = settlements;
        this.operator = operator;
    }

    /** {@code SettlementCalculateAction.execute} (SETTLE-R01). */
    public CalculatedSettlement calculate(SettlementRequest request) {
        int claimId = request.resolvedClaimId();
        // SETTLE-R14: limit from the claim's policy, 10000 when either is missing.
        double limit = claims.findById(claimId)
                .flatMap(claim -> policies.findLimit(claim.policyId()))
                .orElse(LegacyCoercions.FALLBACK_POLICY_LIMIT);
        SettlementResult result = SettlementCalculator.calculate(
                request.resolvedCoveredAmount(), request.resolvedDeductible(),
                request.resolvedDepreciation(), limit);
        return new CalculatedSettlement(claimId, limit, result);
    }

    /** {@code SettlementSaveAction.execute} (SETTLE-R02). */
    @Transactional
    public SettlementRow save(SettlementRequest request) {
        int claimId = request.resolvedClaimId();
        // SETTLE-R15: unlike calculate, save has no fallback limit; a missing claim or
        // policy failed with a NullPointerException in the monolith.
        double limit = claims.findById(claimId)
                .flatMap(claim -> policies.findLimit(claim.policyId()))
                .orElseThrow(() -> new ClaimNotFoundException(claimId));
        // SETTLE-R37: recompute from the request rather than reuse a prior calculation.
        SettlementResult result = SettlementCalculator.calculate(
                request.resolvedCoveredAmount(), request.resolvedDeductible(),
                request.resolvedDepreciation(), limit);
        SettlementRow row = new SettlementRow(
                settlements.nextId(),               // SETTLE-R39
                claimId,
                result.coveredAmount(),             // SETTLE-R42
                result.deductibleApplied(),
                result.depreciation(),
                result.cappedAtLimit(),
                result.settlementAmount(),
                String.valueOf(operator),           // SETTLE-R40, SETTLE-R48
                LEGACY_CALCULATED_DATE);            // SETTLE-R41
        settlements.insert(row);                    // SETTLE-R38
        return row;
    }

    /** {@code SettlementDetailAction.execute} (SETTLE-R03, SETTLE-R45, SETTLE-R46). */
    public Optional<SettlementRow> detail(int claimId) {
        return settlements.findLatestByClaim(claimId);
    }

    /** Read-only claim lookup backing the {@code claim.<id>.status} parity probe (SETTLE-R44). */
    public Optional<ClaimRecord> claim(int claimId) {
        return claims.findById(claimId);
    }

    public String operator() {
        return operator;
    }
}
