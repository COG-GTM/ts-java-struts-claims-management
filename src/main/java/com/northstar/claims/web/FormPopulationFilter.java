package com.northstar.claims.web;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Removes request parameters that address the bean metadata graph before Struts
 * populates an ActionForm from them.
 *
 * <p>Struts binds every request parameter of a {@code *.do} request onto the
 * mapped form bean through Commons BeanUtils. Parameter names such as
 * {@code class.classLoader.resources.dirContext.docBase} walk out of the form
 * bean and into the container class loader, so they are dropped here rather
 * than handed to the population machinery.</p>
 */
public class FormPopulationFilter implements Filter {

    private static final Log log = LogFactory.getLog(FormPopulationFilter.class);

    /** Property names that never belong to a claims form bean. */
    private static final String[] BLOCKED_PROPERTIES = {"class", "classloader"};

    public void init(FilterConfig config) throws ServletException {
        log.info("Claims form population filter initialized");
    }

    public void destroy() {
        log.info("Claims form population filter destroyed");
    }

    public void doFilter(ServletRequest request, ServletResponse response,
            FilterChain chain) throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest)) {
            chain.doFilter(request, response);
            return;
        }
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        Map filtered = filterParameters(httpRequest.getParameterMap());
        if (filtered.size() == httpRequest.getParameterMap().size()) {
            chain.doFilter(request, response);
            return;
        }
        chain.doFilter(new FilteredParameterRequest(httpRequest, filtered), response);
    }

    /** Copies the parameter map without the entries that address bean metadata. */
    private Map filterParameters(Map parameters) {
        Map filtered = new LinkedHashMap();
        for (Object entryObject : parameters.entrySet()) {
            Map.Entry entry = (Map.Entry) entryObject;
            String name = (String) entry.getKey();
            if (isBlocked(name)) {
                log.warn("Rejected form population parameter " + name);
                continue;
            }
            filtered.put(name, entry.getValue());
        }
        return filtered;
    }

    /** Reports whether any property in the parameter path is blocked. */
    static boolean isBlocked(String name) {
        if (name == null) {
            return false;
        }
        for (String token : name.split("\\.")) {
            String property = propertyName(token);
            for (int i = 0; i < BLOCKED_PROPERTIES.length; i++) {
                if (BLOCKED_PROPERTIES[i].equals(property)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Strips indexed and mapped suffixes from one segment of a property path. */
    private static String propertyName(String token) {
        String property = token;
        int bracket = property.indexOf('[');
        if (bracket >= 0) {
            property = property.substring(0, bracket);
        }
        int parenthesis = property.indexOf('(');
        if (parenthesis >= 0) {
            property = property.substring(0, parenthesis);
        }
        return property.trim().toLowerCase();
    }

    /** Exposes only the parameters that survived filtering. */
    private static final class FilteredParameterRequest extends HttpServletRequestWrapper {

        private final Map parameters;

        FilteredParameterRequest(HttpServletRequest request, Map parameters) {
            super(request);
            this.parameters = parameters;
        }

        public String getParameter(String name) {
            String[] values = getParameterValues(name);
            return values == null || values.length == 0 ? null : values[0];
        }

        public String[] getParameterValues(String name) {
            return (String[]) parameters.get(name);
        }

        public Map getParameterMap() {
            return Collections.unmodifiableMap(parameters);
        }

        public Enumeration getParameterNames() {
            return Collections.enumeration(parameters.keySet());
        }
    }
}
