package com.northstar.claims.web;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * Minimal dynamic-proxy servlet doubles used by the web tier tests. Only the
 * handful of methods exercised by LoginAction and AuthFilter are implemented.
 */
final class ServletStubs {

    private ServletStubs() {
    }

    static HttpSession session(Map attributes) {
        return (HttpSession) proxy(HttpSession.class,
                new SessionHandler(attributes));
    }

    static HttpServletRequest request(String uri, Map parameters,
            HttpSession session) {
        return (HttpServletRequest) proxy(HttpServletRequest.class,
                new RequestHandler(uri, parameters, session));
    }

    static HttpServletResponse response(Map record) {
        return (HttpServletResponse) proxy(HttpServletResponse.class,
                new ResponseHandler(record));
    }

    static FilterChain chain(Map record) {
        return (FilterChain) proxy(FilterChain.class,
                new ChainHandler(record));
    }

    private static Object proxy(Class contract, InvocationHandler handler) {
        return Proxy.newProxyInstance(contract.getClassLoader(),
                new Class[] { contract }, handler);
    }

    private abstract static class BaseHandler implements InvocationHandler {

        public Object invoke(Object target, Method method, Object[] args) {
            Object value = handle(method.getName(), args);
            if (value != null) {
                return value;
            }
            Class type = method.getReturnType();
            if (Boolean.TYPE.equals(type)) {
                return Boolean.FALSE;
            }
            if (Integer.TYPE.equals(type)) {
                return new Integer(0);
            }
            if (Long.TYPE.equals(type)) {
                return new Long(0);
            }
            return null;
        }

        abstract Object handle(String name, Object[] args);
    }

    private static final class SessionHandler extends BaseHandler {

        private final Map attributes;

        SessionHandler(Map attributes) {
            this.attributes = attributes;
        }

        Object handle(String name, Object[] args) {
            if ("getAttribute".equals(name)) {
                return attributes.get(args[0]);
            }
            if ("setAttribute".equals(name)) {
                attributes.put(args[0], args[1]);
                return null;
            }
            if ("removeAttribute".equals(name)) {
                attributes.remove(args[0]);
                return null;
            }
            if ("invalidate".equals(name)) {
                attributes.clear();
                return null;
            }
            if ("getId".equals(name)) {
                return "test-session";
            }
            return null;
        }
    }

    private static final class RequestHandler extends BaseHandler {

        private final String uri;
        private final Map parameters;
        private final HttpSession session;
        private final Map attributes = new HashMap();

        RequestHandler(String uri, Map parameters, HttpSession session) {
            this.uri = uri;
            this.parameters = parameters;
            this.session = session;
        }

        Object handle(String name, Object[] args) {
            if ("getParameter".equals(name)) {
                return parameters.get(args[0]);
            }
            if ("getSession".equals(name)) {
                return session;
            }
            if ("getRequestURI".equals(name)) {
                return uri;
            }
            if ("getContextPath".equals(name)) {
                return "/claims";
            }
            if ("getMethod".equals(name)) {
                return "POST";
            }
            if ("getAttribute".equals(name)) {
                return attributes.get(args[0]);
            }
            if ("setAttribute".equals(name)) {
                attributes.put(args[0], args[1]);
                return null;
            }
            return null;
        }
    }

    private static final class ResponseHandler extends BaseHandler {

        private final Map record;

        ResponseHandler(Map record) {
            this.record = record;
        }

        Object handle(String name, Object[] args) {
            if ("sendError".equals(name)) {
                record.put("error", args[0]);
                return null;
            }
            if ("sendRedirect".equals(name)) {
                record.put("redirect", args[0]);
                return null;
            }
            return null;
        }
    }

    private static final class ChainHandler extends BaseHandler {

        private final Map record;

        ChainHandler(Map record) {
            this.record = record;
        }

        Object handle(String name, Object[] args) {
            if ("doFilter".equals(name)) {
                record.put("chained", Boolean.TRUE);
            }
            return null;
        }
    }
}
