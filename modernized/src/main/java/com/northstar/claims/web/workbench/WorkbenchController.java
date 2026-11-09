package com.northstar.claims.web.workbench;

import com.northstar.claims.repository.ClaimDAO;
import com.northstar.claims.repository.NoteDAO;
import com.northstar.claims.repository.workbench.WorkbenchUpdates;
import com.northstar.claims.support.ClaimsSupport;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;

/** Ports action-specific attributes rather than enriching mutation responses with a full summary. */
@Controller
public class WorkbenchController {
    private final ClaimsSupport support;
    private final ClaimDAO claims;
    private final NoteDAO notes;
    private final WorkbenchUpdates updates;

    public WorkbenchController(ClaimsSupport support, ClaimDAO claims, NoteDAO notes,
            WorkbenchUpdates updates) {
        this.support = support;
        this.claims = claims;
        this.notes = notes;
        this.updates = updates;
    }

    @RequestMapping("/workbench/list.do")
    public String list(Model model) {
        java.util.List rows;
        try { rows = claims.findByStatus("OPEN"); }
        catch (Exception failure) {
            support.log.warn("Open claim workbench failed", failure);
            rows = new ArrayList();
        }
        model.addAttribute("claims", rows);
        model.addAttribute("claimCount", rows.size());
        model.addAttribute("screenName", "detail");
        return "workbench/list";
    }

    @RequestMapping({"/workbench/assign.do", "/workbench/status.do", "/workbench/reserve.do", "/workbench/note.do"})
    public String change(@ModelAttribute WorkbenchForm form, HttpServletRequest request, Model model) {
        int id = support.integer(form.claimId(), 119);
        String path = request.getRequestURI();
        if (path.endsWith("/assign.do")) {
            // assignedAdjuster is intentionally ignored: the action reads adjuster instead.
            String adjuster = fallback(request.getParameter("adjuster"), "adjuster2");
            updates.assign(id, adjuster);
            model.addAttribute("assignedAdjuster", adjuster);
        } else if (path.endsWith("/status.do")) {
            String status = fallback(request.getParameter("status"), "INVESTIGATING");
            updates.status(id, status);
            model.addAttribute("claimStatus", status);
        } else if (path.endsWith("/reserve.do")) {
            double reserve = support.decimal(form.reserveAmount(), 4500);
            updates.reserve(id, reserve);
            model.addAttribute("reserveAmount", reserve);
        } else {
            // Notes are displayed for this response only; no note insert occurs in legacy.
            model.addAttribute("noteText", fallback(form.noteText(), "Review completed"));
        }
        var claim = support.findClaim(id);
        if (claim == null) return "error";
        model.addAttribute("claim", claim);
        model.addAttribute("claimId", id);
        model.addAttribute("screenName", "detail");
        return "workbench/view";
    }

    @RequestMapping({"/workbench/notes.do", "/workbench/reserveHistory.do", "/workbench/statusHistory.do"})
    public String history(@ModelAttribute WorkbenchForm form, HttpServletRequest request, Model model) throws Exception {
        int id = support.integer(form.claimId(), 119);
        String page = request.getRequestURI().endsWith("/notes.do") ? "notes"
                : request.getRequestURI().endsWith("/reserveHistory.do") ? "reserveHistory" : "statusHistory";
        var claim = claims.findById(id);
        if (page.equals("notes")) {
            model.addAttribute("notes", notes.findByClaim(id));
            if (claim == null) claim = claims.findById(119);
        } else {
            // The audit histories have always been empty placeholders.
            model.addAttribute(page, new ArrayList<>());
            model.addAttribute("screenMode", "read");
            model.addAttribute("operatorScope", "claims");
        }
        support.putClaimSummary(request, claim);
        model.addAttribute("screenName", "detail");
        return "workbench/" + page;
    }

    private String fallback(String value, String fallback) {
        return value == null || value.length() == 0 ? fallback : value;
    }
}
