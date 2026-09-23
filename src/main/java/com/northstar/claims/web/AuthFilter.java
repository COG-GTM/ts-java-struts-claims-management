package com.northstar.claims.web;

import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Protects claim screens with the session marker established by LoginAction.
 */
public class AuthFilter implements Filter {

    private static final Log log = LogFactory.getLog(AuthFilter.class);
    private static final String SUPERVISOR_ROLE = "SUPERVISOR";

    public void init(FilterConfig config) throws ServletException {
        log.info("Claims authentication filter initialized");
    }

    public void destroy() {
        log.info("Claims authentication filter destroyed");
    }

    public void doFilter(ServletRequest request, ServletResponse response,
            FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String uri = httpRequest.getRequestURI();
        HttpSession session = httpRequest.getSession();
        if (isPublic(uri)) {
            chain.doFilter(request, response);
            return;
        }
        if (session.getAttribute("user") == null) {
            log.debug("Redirecting unauthenticated request for " + uri);
            httpResponse.sendRedirect(httpRequest.getContextPath()
                    + "/login.do");
            return;
        }
        if (isAdministrative(uri) && !hasRole(session, SUPERVISOR_ROLE)) {
            log.warn("Rejecting administrative request for " + uri);
            httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean isAdministrative(String uri) {
        return uri.indexOf("/admin/") >= 0;
    }

    private boolean hasRole(HttpSession session, String role) {
        Object value = session.getAttribute("role");
        return value != null && role.equals(String.valueOf(value));
    }

    private boolean isPublic(String uri) {
        return uri.endsWith("login.do")
                || uri.endsWith("index.jsp")
                || uri.endsWith(".css")
                || uri.endsWith(".gif")
                || uri.endsWith(".jpg");
    }
}
