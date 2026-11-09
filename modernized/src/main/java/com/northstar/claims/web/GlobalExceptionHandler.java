package com.northstar.claims.web;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {
    // Struts' global exception handler forwards to error.jsp without setting an error status.
    @ExceptionHandler(Exception.class)
    public String error(Exception failure) { return "error"; }
}
