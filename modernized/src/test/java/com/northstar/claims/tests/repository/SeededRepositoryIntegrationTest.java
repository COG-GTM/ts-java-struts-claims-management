package com.northstar.claims.tests.repository;

import com.northstar.claims.domain.*;
import com.northstar.claims.repository.*;
import com.northstar.claims.service.ClaimManager;
import com.northstar.claims.service.PolicyManager;
import com.northstar.claims.service.SettlementService;
import com.northstar.claims.support.ClaimsSupport;
import com.northstar.claims.tests.support.SnapshotDatabaseTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Every repository method against a scratch copy of the seeded HSQLDB file database
 * (the committed snapshot taken right after the fixed seed), restored before each test.
 */
class SeededRepositoryIntegrationTest extends SnapshotDatabaseTest {
    @Autowired ClaimDAO claims;
    @Autowired PolicyDAO policies;
    @Autowired AdjusterDAO adjusters;
    @Autowired NoteDAO notes;
    @Autowired PaymentDAO payments;
    @Autowired SettlementDAO settlements;
    @Autowired ReportDAO reports;
    @Autowired ClaimManager claimManager;
    @Autowired PolicyManager policyManager;
    @Autowired SettlementService settlementService;
    @Autowired ClaimsSupport support;

    @BeforeEach
    void restoreSeededDatabase() throws Exception {
        restoreSnapshot(SEEDED);
    }

    @Test
    void deterministicNextIdsMatchLegacy() throws Exception {
        assertEquals(121, support.nextId("CLAIM"));
        assertEquals(61, support.nextId("PAYMENT"));
        // max(id)+1, so ids are deterministic after a write (fnol_submit 121, intake_lenient_date 122).
        // Same column shape as the legacy IntakeSubmitAction insert (adjuster1, supervisor, loss date reused).
        support.update("insert into CLAIM values (121, 'CLM-00121', 1, 'Jane Doe', '2019-03-02', "
                + "'2019-03-02', 'WATER', 'Pipe burst', 'OPEN', 0, 'adjuster1', 'supervisor', '2019-03-02')");
        assertEquals(122, support.nextId("CLAIM"));
        assertEquals("2019-03-02", claims.findById(121).getLossDate());
    }

    @Test
    void claimReadsAndSearches() throws Exception {
        assertEquals(120, claims.count());
        List<?> all = claims.findAll();
        assertEquals(120, all.size());
        assertEquals(1, ((Claim) all.get(0)).getClaimId());
        assertEquals(120, ((Claim) all.get(119)).getClaimId());
        Claim reserved = claims.findById(119);
        assertEquals("CLM-00119", reserved.getClaimNumber());
        assertEquals(9001, reserved.getPolicyId());
        assertEquals("Claimant 119", reserved.getClaimantName());
        assertEquals("2019-01-15", reserved.getLossDate());
        assertEquals("LIABILITY", reserved.getLossType());
        assertEquals("DENIED", reserved.getStatus());
        assertEquals(1500, reserved.getReserveAmount());
        assertEquals("adjuster4", reserved.getAssignedAdjuster());
        assertEquals("supervisor", reserved.getCreatedBy());
        assertNull(claims.findById(121));
        List<?> closed = claims.findByStatus("CLOSED");
        assertFalse(closed.isEmpty());
        assertTrue(closed.stream().allMatch(c -> "CLOSED".equals(((Claim) c).getStatus())));
        assertTrue(closed.stream().anyMatch(c -> ((Claim) c).getClaimId() == 120));
        assertTrue(claims.findByStatus("NOPE").isEmpty());
        assertEquals(List.of(119), claims.search("claim 119", "").stream().map(c -> ((Claim) c).getClaimId()).toList());
        assertEquals(1, claims.search("claim 119", null).size());
        assertEquals(1, claims.search("claim 119", "DENIED").size());
        assertTrue(claims.search("claim 119", "OPEN").isEmpty());
        assertEquals(120, claims.search("", "").size());
    }

    @Test
    void claimWritesAndDelete() throws Exception {
        claims.assign(119, "adjuster2");
        claims.updateStatus(119, "INVESTIGATING");
        claims.updateReserve(119, 4500.00);
        Claim updated = claims.findById(119);
        assertEquals("adjuster2", updated.getAssignedAdjuster());
        assertEquals("INVESTIGATING", updated.getStatus());
        assertEquals(4500.00, updated.getReserveAmount());

        // Legacy quirk: ClaimDAO.insert writes only six columns, but CLAIM.loss_date is NOT NULL,
        // so the DAO insert can never succeed against the real schema (intake must use its own SQL).
        Claim inserted = new Claim();
        inserted.setClaimId(121);
        inserted.setClaimNumber("CLM-00121");
        inserted.setPolicyId(9001);
        inserted.setClaimantName("Jane Doe");
        inserted.setStatus("OPEN");
        inserted.setLossDate("2019-03-02");
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> claims.insert(inserted));
        assertNull(claims.findById(121));
        assertEquals(120, claims.count());

        support.update("insert into CLAIM values (121, 'CLM-00121', 9001, 'Jane Doe', '2019-03-02', "
                + "'2019-03-02', 'WATER', 'Pipe burst', 'OPEN', 0, 'adjuster1', 'supervisor', '2019-03-02')");
        assertEquals(121, claims.count());
        claims.delete(121);
        assertNull(claims.findById(121));
        assertEquals(120, claims.count());
    }

    @Test
    void policyReadsSearchesAndStatus() throws Exception {
        assertEquals(42, policies.count());
        assertEquals(42, policies.findAll().size());
        Policy trap = policies.findById(9001);
        assertEquals("NS-09001", trap.getPolicyNumber());
        assertEquals("AUTO", trap.getLineOfBusiness());
        assertEquals("Cap Trap Policy", trap.getInsuredName());
        assertEquals("9001 Reserve Way", trap.getInsuredAddress());
        assertEquals("2018-01-01", trap.getEffectiveDate());
        assertEquals("2019-12-31", trap.getExpiryDate());
        assertEquals(1000, trap.getPolicyLimit());
        assertEquals(100, trap.getDeductible());
        assertEquals(700, trap.getAnnualPremium());
        assertEquals("ACTIVE", trap.getStatus());
        assertNull(policies.findById(-1));

        List<?> auto = policies.findByLine("AUTO");
        assertFalse(auto.isEmpty());
        assertTrue(auto.stream().allMatch(p -> "AUTO".equals(((Policy) p).getLineOfBusiness())));
        List<String> numbers = auto.stream().map(p -> ((Policy) p).getPolicyNumber()).toList();
        assertEquals(numbers.stream().sorted().toList(), numbers, "ordered by policy_number");
        assertTrue(policies.findByLine("NOPE").isEmpty());

        assertEquals(List.of(9001), policies.search("NS-09001").stream().map(p -> ((Policy) p).getPolicyId()).toList());
        assertEquals(List.of(9002), policies.search("Deductible Trap").stream().map(p -> ((Policy) p).getPolicyId()).toList());
        assertEquals(42, policies.search("").size());

        policies.updateStatus(9001, "CANCELLED");
        assertEquals("CANCELLED", policies.findById(9001).getStatus());
        // No cascade: deleting a policy with children fails on the foreign keys, as in legacy.
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> policies.delete(9001));
        policies.delete(-1);
        assertEquals(42, policies.count());
    }

    @Test
    void adjusterReadsAuthenticateAndDelete() throws Exception {
        assertEquals(6, adjusters.count());
        assertEquals(6, adjusters.findAll().size());
        Adjuster first = adjusters.findById(1);
        assertEquals("adjuster1", first.getUsername());
        assertEquals("legacy1", first.getPassword());
        assertEquals("Alex Morgan", first.getFullName());
        assertEquals("NORTH", first.getRegion());
        assertTrue(first.isActive());
        assertNull(adjusters.findById(99));
        assertEquals("Sam Taylor", adjusters.authenticate("supervisor", "supervisor").getFullName());
        assertNull(adjusters.authenticate("adjuster1", "legacy999"));
        List<String> active = adjusters.findActive().stream().map(a -> ((Adjuster) a).getFullName()).toList();
        assertEquals(6, active.size());
        assertEquals(active.stream().sorted().toList(), active, "ordered by full_name");
        adjusters.delete(5);
        assertEquals(5, adjusters.count());
    }

    @Test
    void noteReadsInsertAndDelete() throws Exception {
        assertEquals(120, notes.count());
        assertEquals(120, notes.findAll().size());
        ClaimNote note = notes.findById(1);
        assertEquals(1, note.getClaimId());
        assertEquals("adjuster1", note.getAuthor());
        assertEquals("2019-02-05", note.getNoteDate());
        assertEquals("Initial review completed for claim 1", note.getNoteText());
        assertEquals(1, notes.findByClaim(1).size());
        assertTrue(notes.findByClaim(999).isEmpty());
        assertEquals(1, notes.findUnused(1).getNoteId());
        assertNull(notes.findUnused(999));

        ClaimNote added = new ClaimNote();
        added.setNoteId(121);
        added.setClaimId(1);
        added.setAuthor("supervisor");
        added.setNoteDate("2019-04-01");
        added.setNoteText("Follow-up");
        notes.insert(added);
        assertEquals(2, notes.findByClaim(1).size());
        assertEquals("Follow-up", notes.findById(121).getNoteText());
        notes.delete(121);
        assertNull(notes.findById(121));
    }

    @Test
    void paymentReadsTotalsInsertAndDelete() throws Exception {
        assertEquals(60, payments.count());
        assertEquals(60, payments.findAll().size());
        Payment last = payments.findById(60);
        assertEquals(60, last.getClaimId());
        assertEquals(60, last.getSettlementId());
        assertEquals("Claimant 60", last.getPayeeName());
        assertEquals(250, last.getAmount());
        assertEquals("CHECK", last.getPaymentMethod());
        assertEquals("CHK-00060", last.getCheckNumber());
        assertEquals("2019-03-15", last.getIssuedDate());
        assertEquals("ISSUED", last.getStatus());
        assertNull(payments.findById(61));
        assertEquals(1, payments.findByClaim(1).size());
        assertTrue(payments.findByClaim(119).isEmpty());
        assertEquals(500, payments.totalIssued(1));
        assertEquals(0, payments.totalIssued(119));

        // The payment_issue scenario: deterministic id 61 and check number CHK-61.
        Payment issued = new Payment();
        issued.setPaymentId(support.nextId("PAYMENT"));
        issued.setClaimId(119);
        issued.setSettlementId(119);
        issued.setPayeeName("Reserved Claimant");
        issued.setAmount(1000.00);
        issued.setPaymentMethod("CHECK");
        issued.setCheckNumber("CHK-" + issued.getPaymentId());
        issued.setIssuedDate("2019-04-01");
        issued.setStatus("ISSUED");
        payments.insert(issued);
        assertEquals(61, issued.getPaymentId());
        assertEquals("CHK-61", payments.findById(61).getCheckNumber());
        assertEquals(1000.00, payments.totalIssued(119));
        assertEquals(62, support.nextId("PAYMENT"));
        payments.delete(61);
        assertEquals(60, payments.count());
    }

    @Test
    void settlementReadsSaveAndDelete() throws Exception {
        assertEquals(120, settlements.count());
        assertEquals(120, settlements.findAll().size());
        Settlement seeded = settlements.findById(120);
        assertEquals(120, seeded.getClaimId());
        assertEquals(2000, seeded.getCoveredAmount());
        assertEquals(5000, seeded.getDeductibleApplied());
        assertEquals(100, seeded.getDepreciation());
        assertFalse(seeded.isCappedAtLimit());
        assertEquals(0, seeded.getSettlementAmount());
        assertEquals("adjuster1", seeded.getCalculatedBy());
        assertEquals("2019-03-01", seeded.getCalculatedDate());
        assertNull(settlements.findById(-1));
        assertEquals(119, settlements.findByClaim(119).getSettlementId());
        assertNull(settlements.findByClaim(999));

        Settlement extra = new Settlement();
        extra.setSettlementId(500);
        extra.setClaimId(119);
        extra.setCoveredAmount(1500);
        extra.setSettlementAmount(1000);
        extra.setCappedAtLimit(true);
        extra.setCalculatedBy("supervisor");
        extra.setCalculatedDate("2019-04-01");
        settlements.save(extra);
        // findByClaim returns the highest settlement id for the claim.
        Settlement latest = settlements.findByClaim(119);
        assertEquals(500, latest.getSettlementId());
        assertTrue(latest.isCappedAtLimit());
        settlements.delete(500);
        assertEquals(119, settlements.findByClaim(119).getSettlementId());
    }

    @Test
    void closedClaimIsCalculatedAndSavedThroughService() throws Exception {
        assertEquals("CLOSED", claims.findById(120).getStatus());
        Settlement saved = settlementService.calculateAndSave(120, 6000, "", 0, 100000);
        assertEquals(6000, saved.getSettlementAmount());
        Settlement latest = settlementService.findByClaim(120);
        assertEquals(10120, latest.getSettlementId());
        assertEquals(6000, latest.getSettlementAmount());
        assertEquals("supervisor", latest.getCalculatedBy());
        assertEquals("CLOSED", claims.findById(120).getStatus(), "calculation never changes claim status");
    }

    @SuppressWarnings("unchecked")
    @Test
    void reportQueriesUseTheFixedAsOfDate() throws Exception {
        List<Map<String, Object>> open = reports.openClaimsByAdjuster();
        assertFalse(open.isEmpty());
        double openCount = open.stream().mapToDouble(r -> (Double) r.get("openCount")).sum();
        assertEquals(claims.findByStatus("OPEN").size() + claims.findByStatus("INVESTIGATING").size(), (int) openCount);

        List<Map<String, Object>> ratio = reports.lossRatioByLine();
        List<String> lines = ratio.stream().map(r -> (String) r.get("lineOfBusiness")).toList();
        assertEquals(lines.stream().sorted().toList(), lines);
        for (Map<String, Object> row : ratio) {
            double premium = (Double) row.get("premiumTotal");
            assertEquals(premium == 0 ? 0 : (Double) row.get("lossTotal") / premium, (Double) row.get("lossRatio"));
        }

        List<Map<String, Object>> aged = reports.agedClaims();
        List<String> buckets = aged.stream().map(r -> (String) r.get("bucket")).toList();
        assertTrue(List.of("0_30", "31_60", "61_90", "91_PLUS").containsAll(buckets));
        assertEquals(buckets.stream().sorted().toList(), buckets);
        int notClosed = claims.count() - claims.findByStatus("CLOSED").size() - claims.findByStatus("DENIED").size();
        assertEquals(notClosed, (int) aged.stream().mapToDouble(r -> (Double) r.get("claimCount")).sum());

        List<Map<String, Object>> counts = reports.claimCounts("status");
        assertEquals(120, (int) counts.stream().mapToDouble(r -> (Double) r.get("count")).sum());
    }

    @Test
    void managersDelegateToRepositories() throws Exception {
        assertEquals("CLM-00119", claimManager.getClaim(119).getClaimNumber());
        assertEquals(claims.findByStatus("CLOSED").size(), claimManager.findByStatus("CLOSED").size());
        assertEquals(1, claimManager.search("claim 119", "").size());
        assertNotNull(claimManager.getOpenClaims());
        assertTrue(claimManager.emptyList().isEmpty());
        claimManager.assign(119, "adjuster3");
        claimManager.changeReserve(119, 10);
        claimManager.changeStatus(119, "OPEN");
        Claim claim = claims.findById(119);
        assertEquals("adjuster3", claim.getAssignedAdjuster());
        assertEquals(10, claim.getReserveAmount());
        assertEquals("OPEN", claim.getStatus());

        assertEquals(1000, policyManager.getPolicy(9001).getPolicyLimit());
        assertEquals(42, policyManager.getPolicies().size());
        assertEquals(policies.findByLine("AUTO").size(), policyManager.findByLine("AUTO").size());
        assertEquals(1, policyManager.search("NS-09002").size());
        assertTrue(policyManager.emptyPolicies().isEmpty());
        policyManager.updateStatus(9002, "LAPSED");
        assertEquals("LAPSED", policies.findById(9002).getStatus());
    }

    @Test
    void supportDatabaseHelpersAndFallbacks() throws Exception {
        assertEquals("CLM-00119", support.findClaim(119).getClaimNumber());
        assertNull(support.findClaim(999));
        assertEquals("Claimant 119", support.selectString("select claimant_name from CLAIM where claim_id = ?", 119));
        assertEquals("", support.selectString("select claimant_name from CLAIM where claim_id = ?", 999));
        assertEquals("", support.selectString("select nope from NOWHERE where id = ?", 1));
        assertEquals(6, support.selectStrings("select username from ADJUSTER").size());
        assertTrue(support.selectStrings("select nope from NOWHERE").isEmpty());
        assertEquals(120, support.countRows("CLAIM"));
        assertEquals(0, support.countRows("NOWHERE"));
        assertEquals(1500, support.selectAmount("select reserve_amount from CLAIM where claim_id = ?", 119));
        assertEquals(0, support.selectAmount("select reserve_amount from CLAIM where claim_id = ?", 999));
        assertEquals(0, support.selectAmount("select nope from NOWHERE where id = ?", 1));
        assertEquals(1, support.update("update CLAIM set status = 'OPEN' where claim_id = 120"));
        assertEquals("OPEN", claims.findById(120).getStatus());
    }
}
