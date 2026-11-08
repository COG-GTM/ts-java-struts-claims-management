package com.northstar.claims.web.intake;

import com.northstar.claims.repository.IntakeRepository;
import com.northstar.claims.support.ClaimsSupport;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Preserves the three Struts intake actions and their HTTP 200 forwards. */
@Controller
public class IntakeController {
    private final ClaimsSupport support;
    private final IntakeRepository intake;

    public IntakeController(ClaimsSupport support, IntakeRepository intake) {
        this.support = support;
        this.intake = intake;
    }

    @RequestMapping("/intake/new.do")
    public String newClaim(Model model) {
        model.addAttribute("intakeMode", "NEW");
        model.addAttribute("defaultLossDate", "04/01/2019");
        model.addAttribute("formTitle", "New first notice of loss");
        model.addAttribute("screenName", "detail");
        return "intake/new";
    }

    @RequestMapping("/intake/submit.do")
    public String submit(@ModelAttribute("intakeForm") IntakeForm form,
            HttpServletRequest request, Model model) throws Exception {
        List<String> errors = new ArrayList<>();
        // Struts validation is disabled; only these ordered action checks apply.
        if (form.claimantName() == null || form.claimantName().trim().isEmpty()) {
            errors.add("errors.claimant.required");
        }
        if (form.description() == null || form.description().trim().isEmpty()) {
            errors.add("errors.description.required");
        }
        if (form.lossDate() != null && !form.lossDate().isEmpty()
                && !form.lossDate().matches("\\d{2}/\\d{2}/\\d{4}")) {
            errors.add("errors.lossdate.format");
        }
        if (!errors.isEmpty()) {
            model.addAttribute("nsValidationErrors", errors);
            return "intake/new";
        }

        String lossDate = support.normalizedDate(form.lossDate());
        int claimId = support.nextId("CLAIM");
        intake.insert(claimId, form.claimantName(), lossDate, form.description());
        request.getSession().setAttribute("lastLossDate", lossDate);
        model.addAttribute("claimId", String.valueOf(claimId));
        model.addAttribute("claimStatus", "OPEN");
        model.addAttribute("screenName", "detail");
        return "intake/confirm";
    }

    @RequestMapping("/intake/confirm.do")
    public String confirm(@RequestParam(required = false) String claimId, Model model) {
        // Confirmation echoes the supplied ID; it does not load a persisted claim/status.
        model.addAttribute("confirmationMode", "READ_ONLY");
        model.addAttribute("claimId", claimId);
        model.addAttribute("screenName", "detail");
        return "intake/confirm";
    }
}
