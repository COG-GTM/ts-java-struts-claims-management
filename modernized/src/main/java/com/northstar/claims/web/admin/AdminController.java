package com.northstar.claims.web.admin;

import com.northstar.claims.repository.AdjusterDAO;
import java.sql.SQLException;
import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

/** Preserves the separately mounted Struts admin module's read-only actions. */
@Controller
@RequestMapping("/admin")
public class AdminController {
    private final AdjusterDAO adjusters;

    public AdminController(AdjusterDAO adjusters) {
        this.adjusters = adjusters;
    }

    // The query and count remain even though the JSP only displays a sample row.
    @RequestMapping("/adjusters.do")
    public String adjusters(Model model, HttpServletResponse response) throws SQLException {
        var rows = adjusters.findAll();
        model.addAttribute("adjusters", rows);
        model.addAttribute("adjusterCount", rows.size());
        // Module-relative Struts forwards point at /admin/WEB-INF/jsp and return 404.
        response.setStatus(404);
        model.addAttribute("adminErrorStatus", 404);
        return "admin/error";
    }

    // These legacy request attributes are deliberately unused by the static table.
    @RequestMapping("/reference.do")
    public String reference(Model model, HttpServletResponse response) {
        model.addAttribute("referenceVersion", "2019.04");
        model.addAttribute("lossTypes", List.of("WATER", "FIRE", "THEFT", "COLLISION"));
        response.setStatus(404);
        model.addAttribute("adminErrorStatus", 404);
        return "admin/error";
    }

    // An absent Struts action raises InvalidPathException rather than a 404.
    @RequestMapping("/editAdjuster.do")
    public String unmappedEdit(Model model, HttpServletResponse response) {
        response.setStatus(500);
        model.addAttribute("adminErrorStatus", 500);
        return "admin/error";
    }
}
