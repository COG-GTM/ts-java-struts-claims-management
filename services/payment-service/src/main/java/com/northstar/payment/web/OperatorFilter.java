package com.northstar.payment.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * The service side of AuthFilter (src/main/java/com/northstar/claims/web/AuthFilter.java:37-40):
 * a payment request without an operator is answered 302 to
 * {@code /claims/login.do} before any controller runs, so nothing is written.
 * The operator is the {@value #HEADER} header, set by the gateway in front of
 * the service from the signed-in session (ADR-002, "Operator identity").
 */
@Component
public class OperatorFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-NorthStar-Operator";
    public static final String LOGIN = "/claims/login.do";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().substring(request.getContextPath().length())
                .startsWith("/payment/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String operator = request.getHeader(HEADER);
        if (operator == null || operator.isBlank()) {
            response.setStatus(HttpServletResponse.SC_FOUND);
            response.setHeader("Location", LOGIN);
            return;
        }
        chain.doFilter(request, response);
    }
}
