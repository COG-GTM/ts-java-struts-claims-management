package com.northstar.claims.web;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
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
 *
 * Every request is protected unless its container-normalized path is an exact
 * member of the public path set.
 */
public class AuthFilter implements Filter {

    private static final Log log = LogFactory.getLog(AuthFilter.class);

    static final Set PUBLIC_PATHS = Collections.unmodifiableSet(new HashSet(
            Arrays.asList(new String[] { "/login.do", "/logout.do" })));

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
        if (hasUnsupportedPathParameter(uri)) {
            log.warn("Rejecting request carrying path parameters");
            httpResponse.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        if (isPublic(matchedPath(httpRequest)) || isAuthenticated(httpRequest)) {
            chain.doFilter(request, response);
            return;
        }
        log.debug("Redirecting unauthenticated request for " + uri);
        httpResponse.sendRedirect(httpRequest.getContextPath() + "/login.do");
    }

    /**
     * Flags path parameters other than the container session identifier, which
     * only ever reach the application as a bypass attempt.
     */
    private boolean hasUnsupportedPathParameter(String uri) {
        if (uri == null) {
            return false;
        }
        int start = uri.indexOf(';');
        while (start >= 0) {
            int end = uri.indexOf(';', start + 1);
            String parameter = end < 0 ? uri.substring(start + 1)
                    : uri.substring(start + 1, end);
            if (!parameter.toLowerCase().startsWith("jsessionid=")) {
                return true;
            }
            start = end;
        }
        return false;
    }

    private String matchedPath(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        String pathInfo = request.getPathInfo();
        String path = strip(servletPath == null ? "" : servletPath);
        return pathInfo == null ? path : path + strip(pathInfo);
    }

    /** Drops any path parameter the container left on a path segment. */
    private String strip(String path) {
        int marker = path.indexOf(';');
        return marker < 0 ? path : path.substring(0, marker);
    }

    private boolean isAuthenticated(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session != null && session.getAttribute("user") != null;
    }

    private boolean isPublic(String path) {
        return PUBLIC_PATHS.contains(path);
    }
}
