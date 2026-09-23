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
 */
public class AuthFilter implements Filter {

    private static final Log log = LogFactory.getLog(AuthFilter.class);

    /**
     * Paths reachable without a session, matched exactly against the
     * container-normalized path rather than by suffix.
     */
    static final Set<String> PUBLIC_PATHS;

    static {
        Set<String> paths = new HashSet<String>(
                Arrays.asList("/login.do", "/index.jsp"));
        PUBLIC_PATHS = Collections.unmodifiableSet(paths);
    }

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
        if (hasPathParameters(uri)) {
            log.warn("Rejecting request carrying path parameters");
            httpResponse.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        String path = requestPath(httpRequest);
        HttpSession session = httpRequest.getSession();
        if (isPublic(path) || session.getAttribute("user") != null) {
            chain.doFilter(request, response);
            return;
        }
        log.debug("Redirecting unauthenticated request for " + path);
        httpResponse.sendRedirect(httpRequest.getContextPath() + "/login.do");
    }

    /**
     * Builds the path the container used for routing. Path parameters and the
     * context path are already removed from these values, so the result cannot
     * be steered by decorating the raw request URI.
     */
    static String requestPath(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        String pathInfo = request.getPathInfo();
        String path = servletPath == null ? "" : servletPath;
        if (pathInfo != null) {
            path = path + pathInfo;
        }
        return path;
    }

    /**
     * Detects URL path parameters, which the container strips before routing
     * but which remain visible in the raw request URI.
     */
    static boolean hasPathParameters(String uri) {
        return uri != null && uri.indexOf(';') >= 0;
    }

    static boolean isPublic(String path) {
        return PUBLIC_PATHS.contains(path)
                || isStaticResource(path);
    }

    /**
     * Static resources are served outside the Struts mapping; the extension is
     * read from the normalized path so a decorated URI cannot forge it.
     */
    private static boolean isStaticResource(String path) {
        return path.endsWith(".css")
                || path.endsWith(".gif")
                || path.endsWith(".jpg");
    }
}
