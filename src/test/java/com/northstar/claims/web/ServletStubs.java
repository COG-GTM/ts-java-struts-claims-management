package com.northstar.claims.web;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletContext;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionContext;

/** Minimal servlet doubles so Struts actions can be driven from unit tests. */
public final class ServletStubs {

    private ServletStubs() {
    }

    /** In-memory session backed by a map. */
    public static class Session implements HttpSession {

        private final Map attributes = new HashMap();

        public Object getAttribute(String name) {
            return attributes.get(name);
        }

        public void setAttribute(String name, Object value) {
            attributes.put(name, value);
        }

        public void removeAttribute(String name) {
            attributes.remove(name);
        }

        public Enumeration getAttributeNames() {
            return Collections.enumeration(attributes.keySet());
        }

        public long getCreationTime() {
            return 0;
        }

        public String getId() {
            return "stub-session";
        }

        public long getLastAccessedTime() {
            return 0;
        }

        public ServletContext getServletContext() {
            return null;
        }

        public void setMaxInactiveInterval(int interval) {
        }

        public int getMaxInactiveInterval() {
            return 0;
        }

        public HttpSessionContext getSessionContext() {
            return null;
        }

        public Object getValue(String name) {
            return getAttribute(name);
        }

        public String[] getValueNames() {
            return new String[0];
        }

        public void putValue(String name, Object value) {
            setAttribute(name, value);
        }

        public void removeValue(String name) {
            removeAttribute(name);
        }

        public void invalidate() {
            attributes.clear();
        }

        public boolean isNew() {
            return false;
        }
    }

    /** Request carrying only parameters, attributes and a session. */
    public static class Request implements HttpServletRequest {

        private final Map parameters = new HashMap();
        private final Map attributes = new HashMap();
        private final Session session = new Session();

        public Request(String operator) {
            session.setAttribute("user", operator);
        }

        public Request parameter(String name, String value) {
            parameters.put(name, value);
            return this;
        }

        public String getParameter(String name) {
            return (String) parameters.get(name);
        }

        public Map getParameterMap() {
            return parameters;
        }

        public Enumeration getParameterNames() {
            return Collections.enumeration(parameters.keySet());
        }

        public String[] getParameterValues(String name) {
            String value = getParameter(name);
            return value == null ? null : new String[] {value};
        }

        public Object getAttribute(String name) {
            return attributes.get(name);
        }

        public void setAttribute(String name, Object value) {
            attributes.put(name, value);
        }

        public void removeAttribute(String name) {
            attributes.remove(name);
        }

        public Enumeration getAttributeNames() {
            return Collections.enumeration(attributes.keySet());
        }

        public HttpSession getSession() {
            return session;
        }

        public HttpSession getSession(boolean create) {
            return session;
        }

        public String getCharacterEncoding() {
            return "UTF-8";
        }

        public void setCharacterEncoding(String encoding) {
        }

        public int getContentLength() {
            return 0;
        }

        public String getContentType() {
            return null;
        }

        public ServletInputStream getInputStream() throws IOException {
            return null;
        }

        public String getProtocol() {
            return "HTTP/1.1";
        }

        public String getScheme() {
            return "http";
        }

        public String getServerName() {
            return "localhost";
        }

        public int getServerPort() {
            return 8080;
        }

        public BufferedReader getReader() throws IOException {
            return null;
        }

        public String getRemoteAddr() {
            return "127.0.0.1";
        }

        public String getRemoteHost() {
            return "localhost";
        }

        public Locale getLocale() {
            return Locale.US;
        }

        public Enumeration getLocales() {
            return Collections.enumeration(
                    new ArrayList(Collections.singletonList(Locale.US)));
        }

        public boolean isSecure() {
            return false;
        }

        public RequestDispatcher getRequestDispatcher(String path) {
            return null;
        }

        public String getRealPath(String path) {
            return path;
        }

        public int getRemotePort() {
            return 0;
        }

        public String getLocalName() {
            return "localhost";
        }

        public String getLocalAddr() {
            return "127.0.0.1";
        }

        public int getLocalPort() {
            return 8080;
        }

        public String getAuthType() {
            return null;
        }

        public Cookie[] getCookies() {
            return new Cookie[0];
        }

        public long getDateHeader(String name) {
            return -1;
        }

        public String getHeader(String name) {
            return null;
        }

        public Enumeration getHeaders(String name) {
            return Collections.enumeration(new ArrayList());
        }

        public Enumeration getHeaderNames() {
            return Collections.enumeration(new ArrayList());
        }

        public int getIntHeader(String name) {
            return -1;
        }

        public String getMethod() {
            return "GET";
        }

        public String getPathInfo() {
            return null;
        }

        public String getPathTranslated() {
            return null;
        }

        public String getContextPath() {
            return "/claims";
        }

        public String getQueryString() {
            return null;
        }

        public String getRemoteUser() {
            return null;
        }

        public boolean isUserInRole(String role) {
            return false;
        }

        public Principal getUserPrincipal() {
            return null;
        }

        public String getRequestedSessionId() {
            return session.getId();
        }

        public String getRequestURI() {
            return "/claims/stub.do";
        }

        public StringBuffer getRequestURL() {
            return new StringBuffer("http://localhost:8080/claims/stub.do");
        }

        public String getServletPath() {
            return "/stub.do";
        }

        public boolean isRequestedSessionIdValid() {
            return true;
        }

        public boolean isRequestedSessionIdFromCookie() {
            return true;
        }

        public boolean isRequestedSessionIdFromURL() {
            return false;
        }

        public boolean isRequestedSessionIdFromUrl() {
            return false;
        }
    }

    /** Response that only records the status code actions set. */
    public static class Response implements HttpServletResponse {

        private int status = 200;

        public int status() {
            return status;
        }

        public void setStatus(int value) {
            status = value;
        }

        public void setStatus(int value, String message) {
            status = value;
        }

        public void sendError(int value) throws IOException {
            status = value;
        }

        public void sendError(int value, String message) throws IOException {
            status = value;
        }

        public void sendRedirect(String location) throws IOException {
            status = SC_FOUND;
        }

        public void addCookie(Cookie cookie) {
        }

        public boolean containsHeader(String name) {
            return false;
        }

        public String encodeURL(String url) {
            return url;
        }

        public String encodeRedirectURL(String url) {
            return url;
        }

        public String encodeUrl(String url) {
            return url;
        }

        public String encodeRedirectUrl(String url) {
            return url;
        }

        public void setDateHeader(String name, long value) {
        }

        public void addDateHeader(String name, long value) {
        }

        public void setHeader(String name, String value) {
        }

        public void addHeader(String name, String value) {
        }

        public void setIntHeader(String name, int value) {
        }

        public void addIntHeader(String name, int value) {
        }

        public String getCharacterEncoding() {
            return "UTF-8";
        }

        public String getContentType() {
            return "text/html";
        }

        public ServletOutputStream getOutputStream() throws IOException {
            return null;
        }

        public PrintWriter getWriter() throws IOException {
            return null;
        }

        public void setCharacterEncoding(String encoding) {
        }

        public void setContentLength(int length) {
        }

        public void setContentType(String type) {
        }

        public void setBufferSize(int size) {
        }

        public int getBufferSize() {
            return 0;
        }

        public void flushBuffer() throws IOException {
        }

        public void resetBuffer() {
        }

        public boolean isCommitted() {
            return false;
        }

        public void reset() {
        }

        public void setLocale(Locale locale) {
        }

        public Locale getLocale() {
            return Locale.US;
        }
    }
}
