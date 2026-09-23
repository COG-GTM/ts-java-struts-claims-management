package com.northstar.claims.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.FilterChain;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.junit.Test;

/** Verifies that the claims authentication filter cannot be bypassed. */
public class AuthFilterTest {

    private final AuthFilter filter = new AuthFilter();

    @Test
    public void pathParameterSuffixNoLongerLooksPublic() throws Exception {
        Recorder recorder = invoke("/claims/workbenchView.do;x.css",
                "/workbenchView.do", null);
        assertFalse("bypassed filter", recorder.chained);
        assertEquals(Integer.valueOf(400), recorder.errorStatus);
        assertNull(recorder.redirect);
    }

    @Test
    public void unauthenticatedActionRedirectsToLogin() throws Exception {
        Recorder recorder = invoke("/claims/workbenchView.do",
                "/workbenchView.do", null);
        assertFalse(recorder.chained);
        assertEquals("/claims/login.do", recorder.redirect);
    }

    @Test
    public void loginScreenStaysPublic() throws Exception {
        Recorder recorder = invoke("/claims/login.do", "/login.do", null);
        assertTrue(recorder.chained);
        assertNull(recorder.redirect);
    }

    @Test
    public void authenticatedActionProceeds() throws Exception {
        Recorder recorder = invoke("/claims/workbenchView.do",
                "/workbenchView.do", "supervisor");
        assertTrue(recorder.chained);
        assertNull(recorder.redirect);
    }

    @Test
    public void allowListMatchesNormalizedPathsExactly() {
        assertTrue(AuthFilter.isPublic("/login.do"));
        assertFalse(AuthFilter.isPublic("/workbenchView.do"));
        assertFalse(AuthFilter.isPublic("/adminlogin.do"));
        assertTrue(AuthFilter.hasPathParameters("/claims/workbenchView.do;x.css"));
        assertFalse(AuthFilter.hasPathParameters("/claims/workbenchView.do"));
    }

    private Recorder invoke(String requestUri, String servletPath, String user)
            throws Exception {
        Recorder recorder = new Recorder();
        Map<String, Object> sessionAttributes = new HashMap<String, Object>();
        if (user != null) {
            sessionAttributes.put("user", user);
        }
        HttpSession session = (HttpSession) proxy(HttpSession.class,
                new SessionHandler(sessionAttributes));
        HttpServletRequest request = (HttpServletRequest)
                proxy(HttpServletRequest.class,
                        new RequestHandler(requestUri, servletPath, session));
        HttpServletResponse response = (HttpServletResponse)
                proxy(HttpServletResponse.class, new ResponseHandler(recorder));
        filter.doFilter(request, response, new RecordingChain(recorder));
        return recorder;
    }

    private static Object proxy(Class<?> type, InvocationHandler handler) {
        return Proxy.newProxyInstance(type.getClassLoader(),
                new Class<?>[] {type}, handler);
    }

    private static class Recorder {
        private boolean chained;
        private String redirect;
        private Integer errorStatus;
    }

    private static class RecordingChain implements FilterChain {
        private final Recorder recorder;

        RecordingChain(Recorder recorder) {
            this.recorder = recorder;
        }

        public void doFilter(ServletRequest request, ServletResponse response) {
            recorder.chained = true;
        }
    }

    private static class RequestHandler implements InvocationHandler {
        private final String requestUri;
        private final String servletPath;
        private final HttpSession session;

        RequestHandler(String requestUri, String servletPath,
                HttpSession session) {
            this.requestUri = requestUri;
            this.servletPath = servletPath;
            this.session = session;
        }

        public Object invoke(Object target, Method method, Object[] args) {
            String name = method.getName();
            if ("getRequestURI".equals(name)) {
                return requestUri;
            }
            if ("getServletPath".equals(name)) {
                return servletPath;
            }
            if ("getPathInfo".equals(name)) {
                return null;
            }
            if ("getContextPath".equals(name)) {
                return "/claims";
            }
            if ("getSession".equals(name)) {
                return session;
            }
            throw new UnsupportedOperationException(name);
        }
    }

    private static class SessionHandler implements InvocationHandler {
        private final Map<String, Object> attributes;

        SessionHandler(Map<String, Object> attributes) {
            this.attributes = attributes;
        }

        public Object invoke(Object target, Method method, Object[] args) {
            if ("getAttribute".equals(method.getName())) {
                return attributes.get(String.valueOf(args[0]));
            }
            throw new UnsupportedOperationException(method.getName());
        }
    }

    private static class ResponseHandler implements InvocationHandler {
        private final Recorder recorder;

        ResponseHandler(Recorder recorder) {
            this.recorder = recorder;
        }

        public Object invoke(Object target, Method method, Object[] args) {
            String name = method.getName();
            if ("sendRedirect".equals(name)) {
                recorder.redirect = String.valueOf(args[0]);
                return null;
            }
            if ("sendError".equals(name)) {
                recorder.errorStatus = (Integer) args[0];
                return null;
            }
            throw new UnsupportedOperationException(name);
        }
    }
}
