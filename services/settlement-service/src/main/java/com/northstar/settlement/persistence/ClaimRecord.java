package com.northstar.settlement.persistence;

/**
 * The slice of the legacy {@code CLAIM} row the settlement seam reads: the policy
 * link for the limit lookup (SETTLE-R14) and the status the parity probe observes
 * unchanged after a calculation (SETTLE-R44).
 */
public record ClaimRecord(int claimId, int policyId, String status) {
}
