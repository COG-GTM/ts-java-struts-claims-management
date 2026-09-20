package com.northstar.settlement.domain;

/** SETTLE-R06 v2 (CHG-001): a deductible that is neither blank nor a number. */
public class InvalidDeductibleException extends RuntimeException {

    public static final String KEY = "settlement.deductible.invalid";

    public InvalidDeductibleException(String value) {
        super("Deductible is not a number: " + value);
    }
}
