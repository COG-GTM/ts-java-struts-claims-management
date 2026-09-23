package com.northstar.claims;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.junit.Test;
import com.northstar.claims.web.AuthFilter;

/** Confirms the filter protects every action that is not explicitly public. */
public class AuthFilterTest {

    /** Records what the filter did with a request. */
    private static class Outcome {
        boolean chained;
        String redirect;
        int error;
    }

    @Test
    public void rejectsPathParameterDisguisedAsStaticAsset() throws Exception {
        Outcome outcome = run("/claims/workbenchView.do;x.css",
                "/workbenchView.do", false);
        assertFalse(outcome.chained);
        assertEquals(HttpServletResponse.SC_BAD_REQUEST, outcome.error);
    }

    @Test
    public void rejectsPathParameterOnPublicLoginPath() throws Exception {
        Outcome outcome = run("/claims/login.do;x.jpg", "/login.do", false);
        assertFalse(outcome.chained);
        assertEquals(HttpServletResponse.SC_BAD_REQUEST, outcome.error);
    }

    @Test
    public void redirectsUnauthenticatedAction() throws Exception {
        Outcome outcome = run("/claims/paymentIssue.do", "/paymentIssue.do",
                false);
        assertFalse(outcome.chained);
        assertEquals("/claims/login.do", outcome.redirect);
    }

    @Test
    public void redirectsActionNamedLikeAPublicPath() throws Exception {
        Outcome outcome = run("/claims/adminlogin.do", "/adminlogin.do", false);
        assertFalse(outcome.chained);
        assertEquals("/claims/login.do", outcome.redirect);
    }

    @Test
    public void allowsLoginScreen() throws Exception {
        assertTrue(run("/claims/login.do", "/login.do", false).chained);
    }

    @Test
    public void allowsRewrittenSessionIdOnPublicPath() throws Exception {
        assertTrue(run("/claims/login.do;jsessionid=ABC123",
                "/login.do;jsessionid=ABC123", false).chained);
    }

    @Test
    public void redirectsRewrittenSessionIdOnProtectedPath() throws Exception {
        Outcome outcome = run("/claims/workbenchView.do;jsessionid=ABC123",
                "/workbenchView.do;jsessionid=ABC123", false);
        assertFalse(outcome.chained);
        assertEquals("/claims/login.do", outcome.redirect);
    }

    @Test
    public void allowsAuthenticatedAction() throws Exception {
        assertTrue(run("/claims/paymentIssue.do", "/paymentIssue.do",
                true).chained);
    }

    private Outcome run(String requestUri, String servletPath,
            boolean authenticated) throws Exception {
        final Outcome outcome = new Outcome();
        Map attributes = new HashMap();
        if (authenticated) {
            attributes.put("user", "supervisor");
        }
        HttpSession session = session(attributes);
        HttpServletRequest request = request(requestUri, servletPath,
                authenticated ? session : null);
        HttpServletResponse response = response(outcome);
        FilterChain chain = (FilterChain) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class[] { FilterChain.class },
                new InvocationHandler() {
                    public Object invoke(Object proxy, Method method,
                            Object[] args) {
                        outcome.chained = true;
                        return null;
                    }
                });
        new AuthFilter().doFilter(request, response, chain);
        return outcome;
    }

    private HttpSession session(final Map attributes) {
        return (HttpSession) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class[] { HttpSession.class },
                new InvocationHandler() {
                    public Object invoke(Object proxy, Method method,
                            Object[] args) {
                        if ("getAttribute".equals(method.getName())) {
                            return attributes.get(args[0]);
                        }
                        return null;
                    }
                });
    }

    private HttpServletRequest request(final String requestUri,
            final String servletPath, final HttpSession session) {
        return (HttpServletRequest) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class[] { HttpServletRequest.class },
                new InvocationHandler() {
                    public Object invoke(Object proxy, Method method,
                            Object[] args) {
                        String name = method.getName();
                        if ("getRequestURI".equals(name)) {
                            return requestUri;
                        }
                        if ("getServletPath".equals(name)) {
                            return servletPath;
                        }
                        if ("getContextPath".equals(name)) {
                            return "/claims";
                        }
                        if ("getSession".equals(name)) {
                            return session;
                        }
                        return null;
                    }
                });
    }

    private HttpServletResponse response(final Outcome outcome) {
        return (HttpServletResponse) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class[] { HttpServletResponse.class },
                new InvocationHandler() {
                    public Object invoke(Object proxy, Method method,
                            Object[] args) {
                        if ("sendRedirect".equals(method.getName())) {
                            outcome.redirect = (String) args[0];
                        }
                        if ("sendError".equals(method.getName())) {
                            outcome.error = ((Integer) args[0]).intValue();
                        }
                        return null;
                    }
                });
    }
}
