package com.northstar.claims.web.settlement;

import com.northstar.claims.domain.Settlement;
import com.northstar.claims.repository.PolicyDAO;
import com.northstar.claims.repository.SettlementDAO;
import com.northstar.claims.service.SettlementCalculator;
import com.northstar.claims.support.ClaimsSupport;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Preserves the three Struts forwards without introducing validation or redirects. */
@Controller
public class SettlementController {
    private final ClaimsSupport support;
    private final PolicyDAO policies;
    private final SettlementDAO settlements;
    private final SettlementCalculator calculator;

    public SettlementController(ClaimsSupport support, PolicyDAO policies,
            SettlementDAO settlements, SettlementCalculator calculator) {
        this.support = support;
        this.policies = policies;
        this.settlements = settlements;
        this.calculator = calculator;
    }

    @RequestMapping("/settlement/calculate.do")
    public String calculate(@ModelAttribute SettlementForm form, Model model) throws Exception {
        int claimId = support.integer(form.claimId(), 119);
        var claim = support.findClaim(claimId);
        double limit = 10000;
        if (claim != null) {
            var policy = policies.findById(claim.getPolicyId());
            if (policy != null) limit = policy.getPolicyLimit();
        }
        model.addAttribute("settlement", calculate(form, claimId, limit));
        model.addAttribute("policyLimit", limit);
        model.addAttribute("screenName", "detail");
        return "settlement/calculate";
    }

    @RequestMapping("/settlement/save.do")
    public String save(@ModelAttribute SettlementForm form, Model model, HttpSession session)
            throws Exception {
        int claimId = support.integer(form.claimId(), 119);
        var claim = support.findClaim(claimId);
        // Save intentionally lacks calculate's missing-claim/policy fallback.
        var policy = policies.findById(claim.getPolicyId());
        var value = calculate(form, claimId, policy.getPolicyLimit());
        value.setSettlementId(support.nextId("SETTLEMENT"));
        value.setCalculatedBy(String.valueOf(session.getAttribute("user")));
        value.setCalculatedDate("2019-04-01");
        settlements.save(value);
        model.addAttribute("settlement", value);
        model.addAttribute("settlementAmount", value.getSettlementAmount());
        model.addAttribute("screenName", "detail");
        return "settlement/save";
    }

    @RequestMapping("/settlement/detail.do")
    public String detail(@RequestParam(required = false) String claimId, Model model)
            throws Exception {
        var value = settlements.findByClaim(support.integer(claimId, 119));
        // Legacy bean:write on calculatedBy fails when no settlement exists.
        if (value == null) return "error";
        model.addAttribute("settlement", value);
        model.addAttribute("screenName", "detail");
        model.addAttribute("screenMode", "read");
        model.addAttribute("operatorScope", "claims");
        return "settlement/detail";
    }

    private Settlement calculate(SettlementForm form, int claimId, double limit) {
        // policyLimit from the form is ignored: the policy record supplies the cap.
        var value = calculator.calculate(support.decimal(form.coveredAmount(), 5000),
                form.deductible() == null || form.deductible().isEmpty() ? "0" : form.deductible(),
                support.decimal(form.depreciation(), 0), limit);
        value.setClaimId(claimId);
        return value;
    }
}
