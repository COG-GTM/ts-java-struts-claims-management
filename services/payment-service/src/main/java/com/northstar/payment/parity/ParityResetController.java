package com.northstar.payment.parity;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test fixture control for {@code make parity}, available only under the
 * {@code parity} Spring profile (make payment-run-parity). It is not part of
 * the payment slice: the legacy application has no equivalent.
 *
 * {@code POST /parity/reset?precondition=a.sql&precondition=b.sql} re-executes
 * the monolith's schema.sql (which drops and recreates every table) and
 * seed.sql, then
 * executes each named script from {@code parity.preconditions.dir}, in order.
 * This is how every scenario in parity/routes.json states its database
 * precondition explicitly instead of depending on the order scenarios run in
 * (ADR-002, "How parity is judged"). Script names are bare file names; a path
 * that leaves the preconditions directory is refused.
 *
 * {@code GET /parity/payment/{id}} returns the stored PAYMENT row as the
 * database holds it ({@code amount} as {@code Double.toString}, so 12.345 stays
 * 12.345 and NaN is "NaN"), which is how the parity run checks PAY-R05 and
 * PAY-R09 against the live run's JDBC probe instead of through the rounding
 * detail screen. The profile listens on 127.0.0.1 only.
 */
@Profile("parity")
@RestController
public class ParityResetController {

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;
    private final File legacyDbDir;
    private final File preconditionsDir;

    public ParityResetController(DataSource dataSource, JdbcTemplate jdbc,
            @Value("${legacy.db.dir}") String legacyDbDir,
            @Value("${parity.preconditions.dir}") String preconditionsDir) throws IOException {
        this.dataSource = dataSource;
        this.jdbc = jdbc;
        this.legacyDbDir = new File(legacyDbDir).getCanonicalFile();
        this.preconditionsDir = new File(preconditionsDir).getCanonicalFile();
    }

    @PostMapping("/parity/reset")
    public ResponseEntity<List<String>> reset(
            @RequestParam(name = "precondition", required = false) List<String> preconditions)
            throws IOException {
        List<File> scripts = new ArrayList<>();
        scripts.add(new File(legacyDbDir, "schema.sql"));
        scripts.add(new File(legacyDbDir, "seed.sql"));
        List<String> applied = new ArrayList<>();
        for (String name : preconditions == null ? List.<String>of() : preconditions) {
            File script = new File(preconditionsDir, name).getCanonicalFile();
            if (!script.getPath().startsWith(preconditionsDir.getPath() + File.separator) || !script.isFile()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(List.of("unknown precondition " + name));
            }
            scripts.add(script);
            applied.add(name);
        }
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        for (File script : scripts) {
            populator.addScript(new FileSystemResource(script));
        }
        populator.execute(dataSource);
        return ResponseEntity.ok(applied);
    }

    @GetMapping("/parity/payment/{id}")
    public ResponseEntity<Map<String, String>> storedPayment(@PathVariable int id) {
        List<Map<String, String>> rows = jdbc.query(
                "select * from PAYMENT where payment_id = ?",
                (rs, index) -> {
                    Map<String, String> row = new LinkedHashMap<>();
                    row.put("payment_id", String.valueOf(rs.getInt("payment_id")));
                    row.put("claim_id", String.valueOf(rs.getInt("claim_id")));
                    row.put("settlement_id", String.valueOf(rs.getInt("settlement_id")));
                    row.put("payee_name", rs.getString("payee_name"));
                    row.put("amount", Double.toString(rs.getDouble("amount")));
                    row.put("payment_method", rs.getString("payment_method"));
                    row.put("check_number", rs.getString("check_number"));
                    row.put("issued_date", String.valueOf(rs.getDate("issued_date")));
                    row.put("status", rs.getString("status"));
                    return row;
                }, id);
        return rows.isEmpty() ? ResponseEntity.notFound().build() : ResponseEntity.ok(rows.get(0));
    }
}
