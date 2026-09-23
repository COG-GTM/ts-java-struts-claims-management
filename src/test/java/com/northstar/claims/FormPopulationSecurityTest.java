package com.northstar.claims;

import java.util.Set;

import org.apache.commons.beanutils.PropertyUtils;
import org.junit.Test;

import com.northstar.claims.web.FormPropertyAllowlist;
import com.northstar.claims.web.form.LoginForm;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Covers the request-parameter restrictions applied before Struts populates a
 * form bean, and the patched Commons BeanUtils behaviour they rely on.
 */
public class FormPopulationSecurityTest {

    @Test
    public void declaredPropertiesArePopulated() {
        Set<String> allowed = FormPropertyAllowlist.allowedProperties(new LoginForm());
        assertTrue(FormPropertyAllowlist.isAllowedParameter(allowed, "username"));
        assertTrue(FormPropertyAllowlist.isAllowedParameter(allowed, "password"));
        assertTrue(FormPropertyAllowlist.isAllowedParameter(allowed,
                "org.apache.struts.taglib.html.CANCEL"));
    }

    @Test
    public void classLoaderAndUndeclaredParametersAreRejected() {
        Set<String> allowed = FormPropertyAllowlist.allowedProperties(new LoginForm());
        assertFalse(FormPropertyAllowlist.isAllowedParameter(allowed,
                "class.classLoader.resources.dirContext.docBase"));
        assertFalse(FormPropertyAllowlist.isAllowedParameter(allowed, "class"));
        assertFalse(FormPropertyAllowlist.isAllowedParameter(allowed, "classLoader"));
        assertFalse(FormPropertyAllowlist.isAllowedParameter(allowed, "multipartRequestHandler"));
        assertFalse(FormPropertyAllowlist.isAllowedParameter(allowed, "servlet"));
        assertFalse(FormPropertyAllowlist.isAllowedParameter(allowed, "unknownProperty"));
    }

    @Test
    public void beanUtilsSuppressesTheClassProperty() throws Exception {
        assertNull(PropertyUtils.getPropertyDescriptor(new LoginForm(), "class"));
    }
}
