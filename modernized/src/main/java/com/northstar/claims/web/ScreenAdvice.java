package com.northstar.claims.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class ScreenAdvice {
    @Value("${report.asof.date:2019-04-01}")
    private String asOfDate;

    @ModelAttribute("asOfDate")
    public String asOfDate() { return asOfDate; }
}
