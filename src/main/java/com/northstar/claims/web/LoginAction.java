package com.northstar.claims.web;

import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Handles the LoginAction request in the claims web module.
 * The action loads screen data and completes the configured request workflow.
 */
public class LoginAction extends ClaimsActionSupport {

    public ActionForward execute(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        String username = request.getParameter("username");
        String password = request.getParameter("password");
        boolean valid = username != null && password != null
                && (("supervisor".equals(username)
                        && "supervisor".equals(password))
                    || (username.startsWith("adjuster")
                        && password.startsWith("legacy")));
        if (valid) {
            request.getSession().setAttribute("user", username);
            request.getSession().setAttribute("displayName", username);
            request.getSession().removeAttribute("operatorRegion");
            request.setAttribute("loginStatus", "AUTHENTICATED");
            log.info("Authenticated operator " + username);
            System.out.println("login accepted: " + username);
            request.setAttribute("screenName", "detail");
        return mapping.findForward("home");
        }
        request.setAttribute("message", "login.failed");
        request.setAttribute("loginStatus", "REJECTED");
        request.setAttribute("screenName", "detail");
        return mapping.findForward("login");
    }
}
