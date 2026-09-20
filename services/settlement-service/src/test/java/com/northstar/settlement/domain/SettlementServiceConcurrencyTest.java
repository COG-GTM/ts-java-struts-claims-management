package com.northstar.settlement.domain;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import com.northstar.settlement.persistence.SettlementRow;

@SpringBootTest
class SettlementServiceConcurrencyTest {

    @Autowired
    private SettlementService service;

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * SETTLE-R12. The legacy schema allocates settlement_id as
     * max(settlement_id) + 1 with no sequence; concurrent saves must still
     * each get their own row.
     */
    @Test
    void concurrentSavesAllocateDistinctIds() throws Exception {
        int saves = 32;
        int before = jdbc.queryForObject("select count(*) from SETTLEMENT",
                Integer.class);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<SettlementRow>> work = new ArrayList<>();
            for (int i = 0; i < saves; i++) {
                work.add(() -> service.save("119", "5000.00", "500.00", "0.00"));
            }
            List<Integer> ids = new ArrayList<>();
            for (Future<SettlementRow> future : pool.invokeAll(work)) {
                ids.add(future.get().settlementId());
            }
            assertThat(ids.stream().collect(Collectors.toSet())).hasSize(saves);
        } finally {
            pool.shutdownNow();
        }
        int after = jdbc.queryForObject("select count(*) from SETTLEMENT",
                Integer.class);
        assertThat(after - before).isEqualTo(saves);
    }
}
