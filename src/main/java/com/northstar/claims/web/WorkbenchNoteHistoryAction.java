package com.northstar.claims.web;

import com.northstar.claims.dao.NoteDAO;
import com.northstar.claims.model.ClaimNote;
import com.northstar.claims.dao.ClaimDAO;
import com.northstar.claims.model.Claim;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Loads the notes screen data and selects its configured forward. */
public class WorkbenchNoteHistoryAction extends ClaimsActionSupport {

    public ActionForward execute(ActionMapping mapping, ActionForm form,
            HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        int id = integer(request.getParameter("claimId"), 119);
        Claim claim = authorizedClaim(request, id);
        if (claim == null) {
            return denied(mapping, request);
        }
        request.setAttribute("notes", new NoteDAO().findByClaim(id));
        putClaimSummary(request, claim);
        request.setAttribute("screenName", "detail");
        return mapping.findForward("notes");
    }
}
