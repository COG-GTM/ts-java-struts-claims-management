package com.northstar.claims.web.admin;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Keeps Jetty's absolute login Location for admin without changing shared security. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AdminRedirectFilter extends OncePerRequestFilter {
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(request.getContextPath() + "/admin/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        chain.doFilter(request, new HttpServletResponseWrapper(response) {
            @Override
            public void setHeader(String name, String value) {
                // Legacy sendRedirect resolves its context-relative URL against the request origin.
                if ("Location".equalsIgnoreCase(name)
                        && (request.getContextPath() + "/login.do").equals(value)) {
                    String url = request.getRequestURL().toString();
                    value = url.substring(0, url.length() - request.getRequestURI().length()) + value;
                }
                super.setHeader(name, value);
            }
        });
    }
}
