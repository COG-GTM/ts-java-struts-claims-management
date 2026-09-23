package com.northstar.claims.web;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpSession;

/** In-memory {@link HttpSession} stand-in for the action tests. */
final class FakeSession implements InvocationHandler {

    private final Map attributes = new HashMap();
    private HttpSession proxy;

    static FakeSession create() {
        FakeSession handler = new FakeSession();
        handler.proxy = (HttpSession) Proxy.newProxyInstance(
                FakeSession.class.getClassLoader(),
                new Class[] { HttpSession.class }, handler);
        return handler;
    }

    HttpSession session() {
        return proxy;
    }

    public Object invoke(Object target, Method call, Object[] args) {
        String name = call.getName();
        if ("setAttribute".equals(name)) {
            attributes.put(args[0], args[1]);
            return null;
        }
        if ("getAttribute".equals(name)) {
            return attributes.get(args[0]);
        }
        if ("removeAttribute".equals(name)) {
            attributes.remove(args[0]);
            return null;
        }
        if ("getId".equals(name)) {
            return "test-session";
        }
        return FakeRequest.defaultValue(call.getReturnType());
    }
}
