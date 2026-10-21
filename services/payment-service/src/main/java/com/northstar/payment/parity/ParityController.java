package com.northstar.payment.parity;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Parity support, only with {@code parity.enabled=true} (make payment-run-parity)
 * and only from loopback with the {@value #HEADER} header. {@code reset}
 * rebuilds the database from the legacy schema.sql and seed.sql and applies
 * one named section of parity/preconditions/payment.sql, the explicit
 * precondition of a scenario (ADR-002, "How parity is judged"). {@code query}
 * runs one select and returns every column as {@code ResultSet.getString},
 * the same text tools/spec/payment_proof.py records for the legacy database.
 */
@RestController
@RequestMapping("/parity")
@ConditionalOnProperty(name = "parity.enabled", havingValue = "true")
public class ParityController {
    static final String HEADER = "X-Parity-Reset";

    private final JdbcTemplate jdbc;
    private final Path dbDir;
    private final Path preconditions;

    public ParityController(JdbcTemplate jdbc, @Value("${legacy.db.dir}") String dbDir,
            @Value("${parity.preconditions.file}") String preconditions) {
        this.jdbc = jdbc;
        this.dbDir = Path.of(dbDir);
        this.preconditions = Path.of(preconditions);
    }

    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> reset(HttpServletRequest request,
            @RequestHeader(value = HEADER, required = false) String header,
            @RequestParam String scenario) throws IOException {
        if (!allowed(request, header)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        List<String> statements = PreconditionFile.section(Files.readString(preconditions), scenario);
        if (statements == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "no precondition for " + scenario));
        }
        jdbc.execute("DROP SCHEMA PUBLIC CASCADE");
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new FileSystemResource(dbDir.resolve("schema.sql")),
                new FileSystemResource(dbDir.resolve("seed.sql")));
        populator.execute(jdbc.getDataSource());
        for (String statement : statements) {
            jdbc.execute(statement);
        }
        return ResponseEntity.ok(Map.of("scenario", scenario, "statements", statements));
    }

    @PostMapping("/query")
    public ResponseEntity<Map<String, Object>> query(HttpServletRequest request,
            @RequestHeader(value = HEADER, required = false) String header,
            @RequestBody String sql) {
        if (!allowed(request, header)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (!sql.trim().toLowerCase().startsWith("select ")) {
            return ResponseEntity.badRequest().body(Map.of("error", "select only"));
        }
        List<String> columns = new ArrayList<>();
        List<List<String>> rows = jdbc.query(sql.trim(), (ResultSet rs) -> {
            ResultSetMetaData meta = rs.getMetaData();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                columns.add(meta.getColumnLabel(i));
            }
            List<List<String>> out = new ArrayList<>();
            while (rs.next()) {
                List<String> row = new ArrayList<>();
                for (int i = 1; i <= columns.size(); i++) {
                    row.add(rs.getString(i));
                }
                out.add(row);
            }
            return out;
        });
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("columns", columns);
        body.put("rows", rows);
        return ResponseEntity.ok(body);
    }

    private static boolean allowed(HttpServletRequest request, String header) {
        String remote = request.getRemoteAddr();
        boolean loopback = "127.0.0.1".equals(remote) || "0:0:0:0:0:0:0:1".equals(remote)
                || "::1".equals(remote);
        return loopback && "1".equals(header);
    }
}
