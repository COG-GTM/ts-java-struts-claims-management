package com.northstar.claims.web;

import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

/** Resolves every forward name to a forward of the same name. */
final class NamedForwardMapping extends ActionMapping {

    private static final long serialVersionUID = 1L;

    public ActionForward findForward(String name) {
        return new ActionForward(name, "/WEB-INF/jsp/" + name + ".jsp", false);
    }
}
