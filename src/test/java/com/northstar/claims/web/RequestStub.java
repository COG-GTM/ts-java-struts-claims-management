package com.northstar.claims.web;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/** In-memory servlet request and session backed by plain maps. */
public class RequestStub {

    public final Map parameters = new HashMap();
    public final Map attributes = new HashMap();
    public final Map sessionAttributes = new HashMap();

    public RequestStub param(String name, String value) {
        parameters.put(name, value);
        return this;
    }

    public HttpServletRequest request() {
        final HttpSession session = (HttpSession) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class[] { HttpSession.class },
                new MapHandler(sessionAttributes));
        return (HttpServletRequest) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class[] { HttpServletRequest.class },
                new MapHandler(attributes) {
                    protected Object other(String name, Object[] args) {
                        if ("getParameter".equals(name)) {
                            return parameters.get(args[0]);
                        }
                        if ("getSession".equals(name)) {
                            return session;
                        }
                        if ("getContextPath".equals(name)) {
                            return "/claims";
                        }
                        return null;
                    }
                });
    }

    private static class MapHandler implements InvocationHandler {

        private final Map values;

        MapHandler(Map values) {
            this.values = values;
        }

        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if ("getAttribute".equals(name)) {
                return values.get(args[0]);
            }
            if ("setAttribute".equals(name)) {
                values.put(args[0], args[1]);
                return null;
            }
            if ("removeAttribute".equals(name)) {
                values.remove(args[0]);
                return null;
            }
            if ("hashCode".equals(name)) {
                return Integer.valueOf(System.identityHashCode(proxy));
            }
            if ("equals".equals(name)) {
                return Boolean.valueOf(proxy == args[0]);
            }
            if ("toString".equals(name)) {
                return "RequestStub" + values;
            }
            return other(name, args);
        }

        protected Object other(String name, Object[] args) {
            return null;
        }
    }
}
