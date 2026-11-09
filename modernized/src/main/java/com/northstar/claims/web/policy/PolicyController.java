package com.northstar.claims.web.policy;

import com.northstar.claims.domain.Policy;
import com.northstar.claims.repository.PolicyDAO;
import com.northstar.claims.support.ClaimsSupport;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;

/** Read-only policy actions retain their distinct defaults and failure handling. */
@Controller
public class PolicyController {
    private static final Log log = LogFactory.getLog(PolicyController.class);
    private final PolicyDAO policies;
    private final ClaimsSupport support;

    public PolicyController(PolicyDAO policies, ClaimsSupport support) {
        this.policies = policies;
        this.support = support;
    }

    @ModelAttribute("screenName")
    public String screenName() { return "detail"; }

    @RequestMapping("/policy/search.do")
    public String search(PolicySearchForm form, Model model) {
        String line = form.lineOfBusiness();
        if (line == null || line.length() == 0) line = "AUTO";
        List rows;
        try { rows = policies.findByLine(line); }
        catch (Exception failure) {
            log.warn("Policy search failed", failure);
            rows = new ArrayList();
        }
        model.addAttribute("policies", rows);
        model.addAttribute("searchLine", line);
        model.addAttribute("searchCount", rows.size());
        return "policy/search";
    }

    @RequestMapping("/policy/list.do")
    public String list(Model model) {
        List rows;
        try { rows = policies.findAll(); }
        catch (Exception failure) {
            log.warn("Policy list failed", failure);
            rows = new ArrayList();
        }
        model.addAttribute("policies", rows);
        model.addAttribute("policyCount", rows.size());
        return "policy/list";
    }

    @RequestMapping("/policy/view.do")
    public String view(PolicyViewForm form, Model model) throws Exception {
        int id = support.integer(form.policyId(), 1);
        model.addAttribute("policyId", id);
        return detail("view", policies.findById(id), model);
    }

    @RequestMapping("/policy/coverages.do")
    public String coverages(PolicyViewForm form, Model model) throws Exception {
        model.addAttribute("coverages", new ArrayList());
        model.addAttribute("coverageCount", 0);
        return detail("coverages", policies.findById(support.integer(form.policyId(), 1)), model);
    }

    @RequestMapping("/policy/coverageDetail.do")
    public String coverageDetail(PolicyViewForm form, Model model) throws Exception {
        model.addAttribute("coverages", new ArrayList());
        readContext(model);
        return detail("coverageDetail", policies.findById(support.integer(form.policyId(), 9001)), model);
    }

    @RequestMapping("/policy/insuredParty.do")
    public String insuredParty(PolicyViewForm form, Model model) {
        int id = support.integer(form.policyId(), 9001);
        Policy policy;
        try { policy = policies.findById(id); }
        catch (Exception failure) { policy = new Policy(); }
        if (policy == null) policy = new Policy();
        // The original screen overwrites real insured data without persisting it.
        policy.setPolicyId(id);
        policy.setPolicyNumber("POL-" + id);
        policy.setInsuredName("Policy insured party");
        policy.setInsuredAddress("Address on policy record");
        policy.setEffectiveDate("2018-01-01");
        policy.setExpiryDate("2019-12-31");
        policy.setStatus("ACTIVE");
        readContext(model);
        return detail("insuredParty", policy, model);
    }

    @RequestMapping("/policy/renewal.do")
    public String renewal(PolicyViewForm form, Model model) throws Exception {
        Policy policy = policies.findById(support.integer(form.policyId(), 9001));
        if (policy == null) policy = policies.findById(9001);
        readContext(model);
        return detail("renewal", policy, model);
    }

    private void readContext(Model model) {
        model.addAttribute("screenMode", "read");
        model.addAttribute("operatorScope", "claims");
    }

    private String detail(String screen, Policy policy, Model model) {
        // Legacy bean:write fails for a missing policy and invokes the global forward.
        if (policy == null) return "error";
        model.addAttribute("policy", policy);
        return "policy/" + screen;
    }
}
