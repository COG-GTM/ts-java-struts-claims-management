package com.northstar.settlement.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import com.northstar.settlement.persistence.ClaimRepository;
import com.northstar.settlement.persistence.PolicyRepository;
import com.northstar.settlement.persistence.SettlementRepository;

class SettlementServiceTest {

    private final ClaimRepository claims = mock(ClaimRepository.class);
    private final PolicyRepository policies = mock(PolicyRepository.class);
    private final SettlementService service = new SettlementService(
            new SettlementCalculator(), claims, policies,
            mock(SettlementRepository.class), "supervisor");

    /**
     * SETTLE-R05 v2: ClaimsActionSupport.findClaim catches every lookup
     * failure and returns null, so SettlementCalculateAction keeps
     * {@code limit = 10000} and still calculates.
     */
    @Test
    void claimLookupFailureFallsBackToDefaultLimit() {
        when(claims.findPolicyId(anyInt())).thenThrow(
                new DataAccessResourceFailureException("CLAIM unavailable"));

        SettlementResult result = service.calculate("119", "20000", "0", "0");

        assertThat(LegacyDisplay.money(result.settlementAmount()))
                .isEqualTo("10000.00");
        assertThat(result.cappedAtLimit()).isTrue();
    }

    /** SETTLE-R05 v2: the legacy PolicyDAO call is outside findClaim's catch. */
    @Test
    void policyLookupFailureIsSystemError() {
        when(claims.findPolicyId(119)).thenReturn(Optional.of(9001));
        when(policies.findLimit(9001)).thenThrow(
                new DataAccessResourceFailureException("POLICY unavailable"));

        assertThatThrownBy(() -> service.calculate("119", "20000", "0", "0"))
                .isInstanceOf(DataAccessResourceFailureException.class);
    }

    /** SETTLE-R12: save dereferences the claim without a null check. */
    @Test
    void saveDoesNotSwallowClaimLookupFailure() {
        when(claims.findPolicyId(anyInt())).thenThrow(
                new DataAccessResourceFailureException("CLAIM unavailable"));

        assertThatThrownBy(() -> service.save("119", "20000", "0", "0"))
                .isInstanceOf(DataAccessResourceFailureException.class);
    }
}
