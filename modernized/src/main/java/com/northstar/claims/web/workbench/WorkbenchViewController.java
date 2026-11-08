package com.northstar.claims.web.workbench;

import com.northstar.claims.support.ClaimsSupport;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Read-only state probe; ownership passes to the workbench stream. */
@Controller
public class WorkbenchViewController {
    private final ClaimsSupport support;

    public WorkbenchViewController(ClaimsSupport support) { this.support = support; }

    @RequestMapping("/workbench/view.do")
    public String view(@RequestParam(required = false) String claimId, Model model) {
        var claim = support.findClaim(support.integer(claimId, 119));
        // Legacy bean:write fails on a missing claim and forwards to the global error page.
        if (claim == null) return "error";
        model.addAttribute("claim", claim);
        if (claim != null) {
            model.addAttribute("claimId", claim.getClaimId());
            model.addAttribute("claimStatus", claim.getStatus());
            model.addAttribute("reserveAmount", claim.getReserveAmount());
            model.addAttribute("assignedAdjuster", claim.getAssignedAdjuster());
            model.addAttribute("lossDate", claim.getLossDate());
        }
        model.addAttribute("screenName", "detail");
        return "workbench/view";
    }
}
