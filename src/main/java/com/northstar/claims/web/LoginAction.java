package com.northstar.claims.web;

import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.northstar.claims.dao.AdjusterDAO;
import com.northstar.claims.model.Adjuster;

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
        Adjuster operator = new AdjusterDAO().authenticate(username, password);
        if (operator != null) {
            request.getSession().setAttribute("user", operator.getUsername());
            request.getSession().setAttribute("displayName",
                    operator.getUsername());
            request.getSession().setAttribute("role", operator.getRole());
            request.setAttribute("loginStatus", "AUTHENTICATED");
            log.info("Authenticated operator " + operator.getUsername());
            request.setAttribute("screenName", "detail");
        return mapping.findForward("home");
        }
        request.setAttribute("message", "login.failed");
        request.setAttribute("loginStatus", "REJECTED");
        request.setAttribute("screenName", "detail");
        return mapping.findForward("login");
    }
}
