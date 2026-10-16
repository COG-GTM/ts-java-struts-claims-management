package com.northstar.payment.parity;

import jakarta.servlet.http.HttpServletRequest;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Parity replay support, active only under the {@code parity} Spring profile
 * ({@code make payment-run-parity}) and only for loopback callers that send the
 * {@code X-Parity-Reset} header (ADR-002, "How parity is judged").
 *
 * <p>{@code POST /parity/reset?precondition=NAME} drops the in-memory schema,
 * re-runs the legacy schema.sql and seed.sql, then applies
 * {@code parity/preconditions/payment/NAME.sql}, so every scenario in
 * parity/routes.json states its database precondition explicitly instead of
 * depending on the order earlier scenarios ran in. {@code GET /parity/rows}
 * returns the PAYMENT rows as the JDBC driver prints them, which is how
 * tools/spec/PayDbRead.java recorded {@code db_after.payment_rows_after_seed}
 * in docs/specs/SPEC-PAY-001-live.json, so PAY-R05, PAY-R06, PAY-R08 and
 * PAY-R09 are judged on the stored row and not only on the screen.
 */
@RestController
@RequestMapping("/parity")
@Profile("parity")
public class ParityController {
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-]+");
    private static final Set<String> LOOPBACK = Set.of("127.0.0.1", "::1", "0:0:0:0:0:0:0:1");

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;
    private final File legacyDbDir;
    private final File preconditionsDir;

    public ParityController(DataSource dataSource, JdbcTemplate jdbc,
            @Value("${legacy.db.dir}") String legacyDbDir,
            @Value("${parity.preconditions.dir}") String preconditionsDir) {
        this.dataSource = dataSource;
        this.jdbc = jdbc;
        this.legacyDbDir = new File(legacyDbDir);
        this.preconditionsDir = new File(preconditionsDir);
    }

    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> reset(HttpServletRequest request,
            @RequestHeader(value = "X-Parity-Reset", required = false) String header,
            @RequestParam String precondition) {
        if (!LOOPBACK.contains(request.getRemoteAddr()) || header == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "loopback only"));
        }
        if (!NAME.matcher(precondition).matches()) {
            return ResponseEntity.badRequest().body(Map.of("error", "bad precondition name"));
        }
        File sql = new File(preconditionsDir, precondition + ".sql");
        if (!sql.isFile()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "no such precondition"));
        }
        jdbc.execute("DROP SCHEMA PUBLIC CASCADE");
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new FileSystemResource(new File(legacyDbDir, "schema.sql")),
                new FileSystemResource(new File(legacyDbDir, "seed.sql")));
        if (hasStatements(sql)) {
            populator.addScript(new FileSystemResource(sql));
        }
        populator.execute(dataSource);
        return ResponseEntity.ok(Map.of("precondition", precondition,
                "payments", jdbc.queryForObject("select count(*) from PAYMENT", Integer.class)));
    }

    @GetMapping("/rows")
    public ResponseEntity<List<Map<String, String>>> rows(HttpServletRequest request,
            @RequestParam(defaultValue = "0") int minPaymentId) {
        if (!LOOPBACK.contains(request.getRemoteAddr())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(List.of());
        }
        List<Map<String, String>> rows = new ArrayList<>();
        jdbc.query("select * from PAYMENT where payment_id >= ? order by payment_id", rs -> {
            Map<String, String> row = new LinkedHashMap<>();
            for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                row.put(rs.getMetaData().getColumnName(i).toLowerCase(), rs.getString(i));
            }
            rows.add(row);
        }, minPaymentId);
        return ResponseEntity.ok(rows);
    }
    /** The "seed" precondition is comment-only; a script without statements fails to run. */
    private static boolean hasStatements(File sql) {
        try {
            for (String line : java.nio.file.Files.readAllLines(sql.toPath())) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("--")) {
                    return true;
                }
            }
            return false;
        } catch (java.io.IOException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
