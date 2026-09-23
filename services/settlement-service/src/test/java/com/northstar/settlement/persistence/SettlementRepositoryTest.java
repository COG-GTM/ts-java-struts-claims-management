package com.northstar.settlement.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class SettlementRepositoryTest {

    private static final int WRITERS = 32;

    @Autowired
    private SettlementRepository settlements;

    @Autowired
    private JdbcTemplate jdbc;

    /** SETTLE-R12: max(settlement_id)+1 and insert happen under one lock. */
    @Test
    void concurrentSavesAllocateDistinctIds() throws Exception {
        Integer before = jdbc.queryForObject(
                "select max(settlement_id) from SETTLEMENT", Integer.class);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(WRITERS);
        try {
            List<Future<SettlementRow>> saves = new ArrayList<>();
            for (int i = 0; i < WRITERS; i++) {
                Callable<SettlementRow> save = () -> {
                    start.await();
                    return settlements.save(120, 5000.00, 500.00, 0.00, false,
                            4500.00, "supervisor", "2019-04-01");
                };
                saves.add(pool.submit(save));
            }
            start.countDown();
            Set<Integer> ids = new HashSet<>();
            for (Future<SettlementRow> save : saves) {
                ids.add(save.get(30, TimeUnit.SECONDS).settlementId());
            }
            assertThat(ids).hasSize(WRITERS);
            for (int id = before + 1; id <= before + WRITERS; id++) {
                assertThat(ids).contains(id);
            }
            Integer count = jdbc.queryForObject(
                    "select count(*) from SETTLEMENT where settlement_id > ?",
                    Integer.class, before);
            assertThat(count).isEqualTo(WRITERS);
        } finally {
            pool.shutdownNow();
        }
    }
}
