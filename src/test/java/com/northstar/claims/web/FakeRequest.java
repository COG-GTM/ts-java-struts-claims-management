package com.northstar.claims.web;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * Minimal request and session stand-ins for the action authorization tests.
 * Only the accessors the actions use are answered; anything else returns the
 * default value for its type.
 */
final class FakeRequest implements InvocationHandler {

    private final Map parameters = new HashMap();
    private final Map attributes = new HashMap();
    private final HttpSession session;
    private String method = "POST";

    private FakeRequest(HttpSession session) {
        this.session = session;
    }

    static FakeRequest create(String operator) {
        FakeSession session = FakeSession.create();
        FakeRequest request = new FakeRequest(session.session());
        if (operator != null) {
            session.session().setAttribute("user", operator);
            session.session().setAttribute(
                    ClaimsActionSupport.ROLE_ATTRIBUTE,
                    ClaimsAuthorization.roleFor(operator));
        }
        return request;
    }

    HttpServletRequest request() {
        return (HttpServletRequest) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class[] { HttpServletRequest.class }, this);
    }

    FakeRequest method(String value) {
        this.method = value;
        return this;
    }

    FakeRequest parameter(String name, String value) {
        parameters.put(name, value);
        return this;
    }

    FakeRequest withCsrfToken() {
        Object token = session.getAttribute(
                ClaimsActionSupport.CSRF_ATTRIBUTE);
        if (token == null) {
            token = "test-token";
            session.setAttribute(ClaimsActionSupport.CSRF_ATTRIBUTE, token);
        }
        return parameter(ClaimsActionSupport.CSRF_PARAMETER,
                String.valueOf(token));
    }

    Object attribute(String name) {
        return attributes.get(name);
    }

    public Object invoke(Object proxy, Method call, Object[] args) {
        String name = call.getName();
        if ("getParameter".equals(name)) {
            return parameters.get(args[0]);
        }
        if ("getMethod".equals(name)) {
            return method;
        }
        if ("getSession".equals(name)) {
            return session;
        }
        if ("setAttribute".equals(name)) {
            attributes.put(args[0], args[1]);
            return null;
        }
        if ("getAttribute".equals(name)) {
            return attributes.get(args[0]);
        }
        if ("getContextPath".equals(name) || "getRequestURI".equals(name)) {
            return "/claims";
        }
        return defaultValue(call.getReturnType());
    }

    static Object defaultValue(Class type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (Boolean.TYPE.equals(type)) {
            return Boolean.FALSE;
        }
        if (Integer.TYPE.equals(type)) {
            return new Integer(0);
        }
        if (Long.TYPE.equals(type)) {
            return new Long(0);
        }
        if (Double.TYPE.equals(type)) {
            return new Double(0);
        }
        return null;
    }
}
