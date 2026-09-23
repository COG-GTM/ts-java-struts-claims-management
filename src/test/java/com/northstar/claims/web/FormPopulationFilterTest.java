package com.northstar.claims.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.FilterChain;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.PropertyUtils;
import org.junit.Test;

import com.northstar.claims.web.form.LoginForm;

/** Verifies that bean metadata parameters never reach Struts form population. */
public class FormPopulationFilterTest {

    /** Captures the request the filter passes down the chain. */
    private static final class CapturingChain implements FilterChain {

        private ServletRequest request;

        public void doFilter(ServletRequest request, ServletResponse response) {
            this.request = request;
        }
    }

    /** Answers the parameter accessors of a request and nothing else. */
    private static final class ParameterHandler implements InvocationHandler {

        private final Map parameters;

        ParameterHandler(Map parameters) {
            this.parameters = parameters;
        }

        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if ("getParameterMap".equals(name)) {
                return parameters;
            }
            if ("getParameterNames".equals(name)) {
                return Collections.enumeration(parameters.keySet());
            }
            if ("getParameterValues".equals(name)) {
                return parameters.get(args[0]);
            }
            if ("getParameter".equals(name)) {
                String[] values = (String[]) parameters.get(args[0]);
                return values == null || values.length == 0 ? null : values[0];
            }
            return null;
        }
    }

    private static HttpServletRequest stubRequest(Map parameters) {
        return (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class[] {HttpServletRequest.class},
                new ParameterHandler(parameters));
    }

    private static Map parameters(String... namesAndValues) {
        Map map = new LinkedHashMap();
        for (int i = 0; i < namesAndValues.length; i += 2) {
            map.put(namesAndValues[i], new String[] {namesAndValues[i + 1]});
        }
        return map;
    }

    private static HttpServletRequest filter(Map parameters) throws Exception {
        CapturingChain chain = new CapturingChain();
        new FormPopulationFilter().doFilter(stubRequest(parameters), null, chain);
        return (HttpServletRequest) chain.request;
    }

    @Test
    public void dropsClassLoaderParameters() throws Exception {
        HttpServletRequest request = filter(parameters(
                "username", "supervisor",
                "class.classLoader.resources.dirContext.docBase", "/tmp/evil",
                "class.module.classLoader.resources.context.parent.pipeline.first.suffix", ".jsp"));

        assertEquals("supervisor", request.getParameter("username"));
        assertNull(request.getParameter("class.classLoader.resources.dirContext.docBase"));
        assertEquals(1, request.getParameterMap().size());
    }

    @Test
    public void dropsIndexedAndMappedClassParameters() throws Exception {
        HttpServletRequest request = filter(parameters(
                "claimId", "CLM-1",
                "Class.classLoader.docBase", "/tmp/evil",
                "items[0].class.classLoader.docBase", "/tmp/evil",
                "map(class).classLoader.docBase", "/tmp/evil"));

        assertEquals("CLM-1", request.getParameter("claimId"));
        assertEquals(1, request.getParameterMap().size());
    }

    @Test
    public void passesOrdinaryRequestsThroughUnwrapped() throws Exception {
        CapturingChain chain = new CapturingChain();
        HttpServletRequest original = stubRequest(parameters("policyId", "POL-1", "status", "OPEN"));
        new FormPopulationFilter().doFilter(original, null, chain);

        assertTrue(chain.request == original);
    }

    @Test
    public void blocksWholeSegmentsOnly() {
        assertFalse(FormPopulationFilter.isBlocked("classificationCode"));
        assertFalse(FormPopulationFilter.isBlocked("claim.lossClassification"));
        assertTrue(FormPopulationFilter.isBlocked("class"));
        assertTrue(FormPopulationFilter.isBlocked("classLoader.docBase"));
    }

    /**
     * The filtered map is what Struts hands to Commons BeanUtils, so populating a
     * real form bean from it must only touch declared form properties.
     */
    @Test
    public void filteredParametersPopulateOnlyFormProperties() throws Exception {
        HttpServletRequest request = filter(parameters(
                "username", "supervisor",
                "class.classLoader.resources.dirContext.docBase", "/tmp/evil"));

        LoginForm form = new LoginForm();
        BeanUtils.populate(form, request.getParameterMap());

        assertEquals("supervisor", form.getUsername());
    }

    /**
     * Second line of defence: the pinned Commons BeanUtils must refuse the
     * {@code class} property outright (CVE-2014-0114, fixed in 1.9.2+).
     */
    @Test
    public void beanUtilsSuppressesTheClassProperty() throws Exception {
        try {
            PropertyUtils.getProperty(new LoginForm(), "class");
            fail("commons-beanutils exposes the class property; "
                    + "an unpatched version (< 1.9.2) is on the classpath");
        } catch (NoSuchMethodException expected) {
            assertTrue(expected.getMessage().contains("class"));
        }
    }
}
