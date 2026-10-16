package com.northstar.payment.parity;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Parity-only endpoints (profile "parity", ADR-002 "How parity is judged"):
 * reset to the legacy seed, apply a named precondition script from
 * parity/preconditions/payment, and read rows back with a single SELECT.
 */
@RestController
@Profile("parity")
public class ParityController {

    private static final Pattern SCRIPT_NAME = Pattern.compile("[a-z0-9-]+");

    private final JdbcTemplate jdbc;
    private final Path dbDir;
    private final Path preconditionsDir;

    public ParityController(JdbcTemplate jdbc,
            @Value("${legacy.db.dir}") String dbDir,
            @Value("${parity.preconditions.dir}") String preconditionsDir) {
        this.jdbc = jdbc;
        this.dbDir = Path.of(dbDir);
        this.preconditionsDir = Path.of(preconditionsDir);
    }

    @PostMapping("/parity/reset")
    public Map<String, String> reset() {
        jdbc.execute("DROP SCHEMA PUBLIC CASCADE");
        run(dbDir.resolve("schema.sql"), dbDir.resolve("seed.sql"));
        return Map.of("reset", "seed");
    }

    @PostMapping("/parity/apply")
    public ResponseEntity<Map<String, String>> apply(@RequestParam String script) {
        if (!SCRIPT_NAME.matcher(script).matches()) {
            return ResponseEntity.badRequest().build();
        }
        run(preconditionsDir.resolve(script + ".sql"));
        return ResponseEntity.ok(Map.of("applied", script));
    }

    @GetMapping("/parity/query")
    public ResponseEntity<List<Map<String, String>>> query(@RequestParam String sql) {
        if (!sql.trim().toLowerCase().startsWith("select ") || sql.contains(";")) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(jdbc.query(sql, (rs, row) -> {
            Map<String, String> values = new LinkedHashMap<>();
            for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                values.put(rs.getMetaData().getColumnLabel(i).toLowerCase(), rs.getString(i));
            }
            return values;
        }));
    }

    private void run(Path... scripts) {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        for (Path script : scripts) {
            populator.addScript(new FileSystemResource(script));
        }
        populator.execute(jdbc.getDataSource());
    }
}
