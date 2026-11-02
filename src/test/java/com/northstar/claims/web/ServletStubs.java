package com.northstar.claims.web;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

/** Minimal in-memory servlet objects for exercising Struts actions. */
public final class ServletStubs {

    private ServletStubs() {
    }

    /** Request backed by parameter, attribute, and session maps. */
    public static class Request {
        public final Map parameters = new HashMap();
        public final Map attributes = new HashMap();
        public final Map sessionAttributes = new HashMap();
        public String requestUri = "/claims/home.do";
        public String contextPath = "/claims";

        public Request param(String name, String value) {
            parameters.put(name, value);
            return this;
        }

        public HttpServletRequest asRequest() {
            final HttpSession session = (HttpSession) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class[] { HttpSession.class },
                    new InvocationHandler() {
                        public Object invoke(Object proxy, Method method, Object[] args) {
                            String name = method.getName();
                            if ("getAttribute".equals(name)) {
                                return sessionAttributes.get(args[0]);
                            }
                            if ("setAttribute".equals(name)) {
                                sessionAttributes.put(args[0], args[1]);
                                return null;
                            }
                            if ("removeAttribute".equals(name)) {
                                sessionAttributes.remove(args[0]);
                                return null;
                            }
                            throw new UnsupportedOperationException(name);
                        }
                    });
            return (HttpServletRequest) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class[] { HttpServletRequest.class },
                    new InvocationHandler() {
                        public Object invoke(Object proxy, Method method, Object[] args) {
                            String name = method.getName();
                            if ("getParameter".equals(name)) {
                                return parameters.get(args[0]);
                            }
                            if ("getAttribute".equals(name)) {
                                return attributes.get(args[0]);
                            }
                            if ("setAttribute".equals(name)) {
                                attributes.put(args[0], args[1]);
                                return null;
                            }
                            if ("getSession".equals(name)) {
                                return session;
                            }
                            if ("getRequestURI".equals(name)) {
                                return requestUri;
                            }
                            if ("getContextPath".equals(name)) {
                                return contextPath;
                            }
                            throw new UnsupportedOperationException(name);
                        }
                    });
        }
    }

    /** Response that only records redirects. */
    public static class Response {
        public String redirect;

        public HttpServletResponse asResponse() {
            return (HttpServletResponse) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class[] { HttpServletResponse.class },
                    new InvocationHandler() {
                        public Object invoke(Object proxy, Method method, Object[] args) {
                            if ("sendRedirect".equals(method.getName())) {
                                redirect = (String) args[0];
                                return null;
                            }
                            throw new UnsupportedOperationException(method.getName());
                        }
                    });
        }
    }

    /** Builds a mapping with one local forward per name, each pointing at /name.jsp. */
    public static ActionMapping mapping(String[] forwards) {
        ActionMapping mapping = new ActionMapping();
        for (int i = 0; i < forwards.length; i++) {
            mapping.addForwardConfig(new ActionForward(forwards[i],
                    "/" + forwards[i] + ".jsp", false));
        }
        return mapping;
    }
}
