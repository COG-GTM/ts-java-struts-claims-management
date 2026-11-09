package com.northstar.claims.tests.unit;

import com.northstar.claims.security.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

/** The legacy login rule exactly as LoginAction implemented it. */
class CredentialRuleTest {
    // The bean method is package-private; invoking it keeps this a pure unit test with no context.
    private final AuthenticationManager manager =
            ReflectionTestUtils.invokeMethod(new SecurityConfiguration(), "authenticationManager");

    private Authentication login(String username, String password) {
        return manager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(username, password));
    }

    @Test
    void supervisorSupervisorIsAccepted() {
        Authentication result = login("supervisor", "supervisor");
        assertTrue(result.isAuthenticated());
        assertEquals("supervisor", result.getName());
        assertTrue(result.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_OPERATOR")));
    }

    /**
     * SECURITY FINDING (preserved for parity, not fixed): any username starting with
     * "adjuster" and any password starting with "legacy" is accepted, regardless of the
     * ADJUSTER table, so "adjusterX"/"legacyanything" logs in as a non-existent user.
     */
    @Test
    void securityFinding_adjusterPrefixWithLegacyPrefixPasswordIsAcceptedForAnyUser() {
        assertTrue(login("adjuster1", "legacy1").isAuthenticated());
        assertTrue(login("adjuster1", "legacy999").isAuthenticated());
        assertTrue(login("adjuster", "legacy").isAuthenticated());
        assertTrue(login("adjusterDoesNotExist", "legacyWhatever").isAuthenticated());
        assertEquals("adjusterDoesNotExist", login("adjusterDoesNotExist", "legacyWhatever").getName());
    }

    @Test
    void everythingElseIsRejected() {
        assertThrows(BadCredentialsException.class, () -> login("supervisor", "wrong"));
        assertThrows(BadCredentialsException.class, () -> login("Supervisor", "supervisor"));
        assertThrows(BadCredentialsException.class, () -> login("supervisor", "supervisor "));
        assertThrows(BadCredentialsException.class, () -> login("adjuster1", "wrong"));
        assertThrows(BadCredentialsException.class, () -> login("Adjuster1", "legacy1"));
        assertThrows(BadCredentialsException.class, () -> login("xadjuster1", "legacy1"));
        assertThrows(BadCredentialsException.class, () -> login("adjuster1", "Legacy1"));
        assertThrows(BadCredentialsException.class, () -> login("adjuster1", ""));
        assertThrows(BadCredentialsException.class, () -> login("", ""));
        assertThrows(BadCredentialsException.class, () -> login("adjuster1", null));
    }
}
