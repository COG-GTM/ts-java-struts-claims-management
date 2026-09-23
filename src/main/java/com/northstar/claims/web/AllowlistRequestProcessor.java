package com.northstar.claims.web;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.RequestProcessor;

/**
 * Request processor that restricts form-bean population to the properties a
 * form bean actually declares. The filtered view is applied only while Struts
 * copies request parameters into the form; actions continue to read the
 * original request.
 */
public class AllowlistRequestProcessor extends RequestProcessor {

    /** Populates the form bean from an allowlist-filtered view of the request. */
    protected void processPopulate(HttpServletRequest request, HttpServletResponse response,
            ActionForm form, ActionMapping mapping) throws ServletException {
        if (form == null) {
            super.processPopulate(request, response, form, mapping);
            return;
        }
        Set<String> allowed = FormPropertyAllowlist.allowedProperties(form);
        super.processPopulate(new AllowlistRequest(request, allowed), response, form, mapping);
    }

    /** Request view exposing only the parameters allowed for the form bean. */
    public static final class AllowlistRequest extends HttpServletRequestWrapper {

        private final Map filtered;

        public AllowlistRequest(HttpServletRequest request, Set<String> allowed) {
            super(request);
            Map source = request.getParameterMap();
            Map accepted = new HashMap();
            for (Object entry : source.entrySet()) {
                String name = (String) ((Map.Entry) entry).getKey();
                if (FormPropertyAllowlist.isAllowedParameter(allowed, name)) {
                    accepted.put(name, ((Map.Entry) entry).getValue());
                }
            }
            filtered = Collections.unmodifiableMap(accepted);
        }

        public String getParameter(String name) {
            String[] values = getParameterValues(name);
            return values == null || values.length == 0 ? null : values[0];
        }

        public String[] getParameterValues(String name) {
            return (String[]) filtered.get(name);
        }

        public Enumeration getParameterNames() {
            return Collections.enumeration(filtered.keySet());
        }

        public Map getParameterMap() {
            return filtered;
        }
    }
}
