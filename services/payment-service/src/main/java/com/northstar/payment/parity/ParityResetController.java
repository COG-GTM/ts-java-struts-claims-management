package com.northstar.payment.parity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Parity-only database reset, active under the {@code parity} profile
 * ({@code make payment-run-parity}). It rebuilds the in-memory database from
 * the legacy schema.sql and seed.sql, then runs one explicit precondition
 * script from parity/preconditions/payment, so each parity scenario states
 * the database it starts from instead of inheriting the previous scenario's.
 * It is absent without that profile and, since it erases issued payments,
 * answers only loopback callers that send the {@value #RESET_HEADER} header:
 * an HTML form cannot set that header and a cross-site script cannot send it
 * without a CORS preflight, which the service does not allow.
 */
@RestController
@Profile("parity")
public class ParityResetController {

    static final String RESET_HEADER = "X-Parity-Reset";

    private static final Pattern SCRIPT_NAME = Pattern.compile("[A-Za-z0-9_-]+\\.sql");

    private final DataSource dataSource;
    private final Path legacyDbDir;
    private final Path preconditionsDir;

    public ParityResetController(DataSource dataSource,
            @Value("${legacy.db.dir}") String legacyDbDir,
            @Value("${parity.preconditions.dir}") String preconditionsDir) {
        this.dataSource = dataSource;
        this.legacyDbDir = Path.of(legacyDbDir);
        this.preconditionsDir = Path.of(preconditionsDir);
    }

    @PostMapping("/parity/reset")
    public ResponseEntity<Map<String, String>> reset(HttpServletRequest request,
            @RequestParam(required = false) String precondition) {
        if (!isLoopback(request.getRemoteAddr()) || request.getHeader(RESET_HEADER) == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new FileSystemResource(legacyDbDir.resolve("schema.sql")));
        populator.addScript(new FileSystemResource(legacyDbDir.resolve("seed.sql")));
        if (precondition != null && !precondition.isEmpty()) {
            Path script = preconditionsDir.resolve(precondition);
            if (!SCRIPT_NAME.matcher(precondition).matches() || !Files.isRegularFile(script)) {
                return ResponseEntity.badRequest().body(Map.of("precondition", precondition));
            }
            populator.addScript(new FileSystemResource(script));
        }
        new JdbcTemplate(dataSource).execute("DROP SCHEMA PUBLIC CASCADE");
        populator.execute(dataSource);
        return ResponseEntity.ok(Map.of("precondition",
                precondition == null ? "" : precondition));
    }

    private static boolean isLoopback(String address) {
        try {
            return java.net.InetAddress.getByName(address).isLoopbackAddress();
        } catch (java.net.UnknownHostException failure) {
            return false;
        }
    }
}
