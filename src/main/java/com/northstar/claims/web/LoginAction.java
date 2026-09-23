package com.northstar.claims.web;

import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import com.northstar.claims.dao.AdjusterDAO;
import com.northstar.claims.model.Adjuster;

/**
 * Handles the LoginAction request in the claims web module.
 * Credentials are checked against the ADJUSTER credential store.
 */
public class LoginAction extends ClaimsActionSupport {

    private final AdjusterDAO adjusters = new AdjusterDAO();

    public ActionForward execute(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        String username = request.getParameter("username");
        String password = request.getParameter("password");
        Adjuster operator = adjusters.authenticate(username, password);
        if (operator != null) {
            HttpSession session = newSession(request);
            session.setAttribute("user", operator.getUsername());
            session.setAttribute("displayName", operator.getUsername());
            request.setAttribute("loginStatus", "AUTHENTICATED");
            log.info("Authenticated operator " + operator.getUsername());
            request.setAttribute("screenName", "detail");
            return mapping.findForward("home");
        }
        log.info("Rejected login attempt");
        request.setAttribute("message", "login.failed");
        request.setAttribute("loginStatus", "REJECTED");
        request.setAttribute("screenName", "detail");
        return mapping.findForward("login");
    }

    /** Replaces any pre-login session so a fixed session id cannot be reused. */
    private HttpSession newSession(HttpServletRequest request) {
        HttpSession existing = request.getSession(false);
        if (existing != null) {
            existing.invalidate();
        }
        return request.getSession(true);
    }
}
