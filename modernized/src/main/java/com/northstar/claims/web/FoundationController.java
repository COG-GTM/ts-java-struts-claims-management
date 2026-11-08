package com.northstar.claims.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class FoundationController {
    private final AuthenticationManager authenticationManager;
    private final HttpSessionSecurityContextRepository repository;

    public FoundationController(AuthenticationManager authenticationManager,
            HttpSessionSecurityContextRepository repository) {
        this.authenticationManager = authenticationManager;
        this.repository = repository;
    }

    @GetMapping("/login.do")
    public String login() { return "login"; }

    @PostMapping("/login.do")
    public String login(@RequestParam(required = false) String username,
            @RequestParam(required = false) String password, HttpServletRequest request,
            HttpServletResponse response, Model model) {
        try {
            var authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(username, password));
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            // A controller forward (200), not formLogin's redirect, matches LoginAction.
            repository.saveContext(context, request, response);
            request.getSession().setAttribute("user", username);
            request.getSession().setAttribute("displayName", username);
            model.addAttribute("loginStatus", "AUTHENTICATED");
            model.addAttribute("screenName", "detail");
            return "home";
        } catch (AuthenticationException failure) {
            model.addAttribute("message", "login.failed");
            model.addAttribute("loginStatus", "REJECTED");
            model.addAttribute("screenName", "detail");
            return "login";
        }
    }

    @RequestMapping("/logout.do")
    public String logout(HttpServletRequest request, Model model) {
        if (request.getSession(false) != null) request.getSession().invalidate();
        SecurityContextHolder.clearContext();
        model.addAttribute("logoutStatus", "COMPLETE");
        model.addAttribute("screenName", "detail");
        return "login";
    }

    @RequestMapping("/home.do")
    public String home(Model model) {
        model.addAttribute("screenName", "home");
        model.addAttribute("screenMode", "read");
        model.addAttribute("operatorScope", "claims");
        model.addAttribute("homeArea", "claims operations");
        model.addAttribute("homeNavigation", "standard");
        model.addAttribute("homeStatus", "available");
        model.addAttribute("homeFormat", "html");
        model.addAttribute("homeRole", "operator");
        model.addAttribute("homeVersion", "claims");
        return "home";
    }
}
