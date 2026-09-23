package com.northstar.claims.web;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.beanutils.DynaBean;
import org.apache.commons.beanutils.DynaProperty;
import org.apache.commons.beanutils.PropertyUtils;

import java.beans.PropertyDescriptor;

/**
 * Determines which request parameter names Struts is allowed to copy into a
 * form bean. Only the simple, writable properties declared by the form bean
 * itself are accepted; every other parameter name is dropped before the
 * Commons BeanUtils population pass runs.
 */
public final class FormPropertyAllowlist {

    /** Property names a request must never populate on a form bean. */
    private static final String[] RESERVED = {
        "class", "classLoader", "classloader", "servlet", "multipartRequestHandler"
    };

    private FormPropertyAllowlist() {
    }

    /** Returns the writable property names declared by the given form bean. */
    public static Set<String> allowedProperties(Object form) {
        Set<String> allowed = new HashSet<String>();
        if (form == null) {
            return allowed;
        }
        if (form instanceof DynaBean) {
            DynaProperty[] properties = ((DynaBean) form).getDynaClass().getDynaProperties();
            for (int i = 0; i < properties.length; i++) {
                allowed.add(properties[i].getName());
            }
        }
        PropertyDescriptor[] descriptors = PropertyUtils.getPropertyDescriptors(form);
        for (int i = 0; i < descriptors.length; i++) {
            if (descriptors[i].getWriteMethod() != null) {
                allowed.add(descriptors[i].getName());
            }
        }
        for (int i = 0; i < RESERVED.length; i++) {
            allowed.remove(RESERVED[i]);
        }
        return Collections.unmodifiableSet(allowed);
    }

    /**
     * Returns true when the parameter name may be populated into the form.
     * Struts control parameters are preserved, nested and indexed expressions
     * are rejected, and everything else must name a declared property.
     */
    public static boolean isAllowedParameter(Set<String> allowed, String name) {
        if (name == null || name.length() == 0) {
            return false;
        }
        if (isStrutsControlParameter(name)) {
            return true;
        }
        if (name.indexOf('.') >= 0 || name.indexOf('[') >= 0 || name.indexOf('(') >= 0) {
            return false;
        }
        for (int i = 0; i < RESERVED.length; i++) {
            if (RESERVED[i].equalsIgnoreCase(name)) {
                return false;
            }
        }
        return allowed.contains(name);
    }

    /** Returns true for the hidden parameters written by the Struts taglibs. */
    private static boolean isStrutsControlParameter(String name) {
        return name.startsWith("org.apache.struts.taglib.html.");
    }
}
