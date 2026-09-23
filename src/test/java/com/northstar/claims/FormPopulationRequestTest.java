package com.northstar.claims;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.beanutils.BeanUtils;
import org.junit.Test;

import com.northstar.claims.web.AllowlistRequestProcessor;
import com.northstar.claims.web.FormPropertyAllowlist;
import com.northstar.claims.web.form.LoginForm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * Verifies the filtered request Struts populates form beans from: declared
 * fields survive, ClassLoader expressions never reach Commons BeanUtils.
 */
public class FormPopulationRequestTest {

    private static HttpServletRequest requestWith(Map parameters) {
        final Map values = parameters;
        InvocationHandler handler = new InvocationHandler() {
            public Object invoke(Object proxy, Method method, Object[] args) {
                String name = method.getName();
                if ("getParameterMap".equals(name)) {
                    return values;
                }
                if ("getParameterValues".equals(name)) {
                    return values.get(args[0]);
                }
                if ("getParameter".equals(name)) {
                    String[] found = (String[]) values.get(args[0]);
                    return found == null ? null : found[0];
                }
                return null;
            }
        };
        return (HttpServletRequest) Proxy.newProxyInstance(
                FormPopulationRequestTest.class.getClassLoader(),
                new Class[] {HttpServletRequest.class}, handler);
    }

    @Test
    public void attackParametersNeverReachBeanUtils() throws Exception {
        Map parameters = new HashMap();
        parameters.put("username", new String[] {"supervisor"});
        parameters.put("password", new String[] {"supervisor"});
        parameters.put("class.classLoader.resources.dirContext.docBase",
                new String[] {"/tmp/pwn"});
        parameters.put("class.classLoader.URLs[0]", new String[] {"file:/tmp/pwn"});

        LoginForm form = new LoginForm();
        ClassLoader before = form.getClass().getClassLoader();
        HttpServletRequest filtered = new AllowlistRequestProcessor.AllowlistRequest(
                requestWith(parameters), FormPropertyAllowlist.allowedProperties(form));

        assertNull(filtered.getParameter("class.classLoader.resources.dirContext.docBase"));
        assertNull(filtered.getParameter("class.classLoader.URLs[0]"));
        assertEquals(2, filtered.getParameterMap().size());

        BeanUtils.populate(form, filtered.getParameterMap());
        assertEquals("supervisor", form.getUsername());
        assertEquals("supervisor", form.getPassword());
        assertSame(before, form.getClass().getClassLoader());
    }
}
