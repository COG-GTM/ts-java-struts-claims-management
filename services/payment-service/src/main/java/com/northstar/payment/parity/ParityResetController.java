package com.northstar.payment.parity;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
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
 */
@Profile("parity")
@RestController
public class ParityResetController {

    private final DataSource dataSource;
    private final File legacyDbDir;
    private final File preconditionsDir;

    public ParityResetController(DataSource dataSource,
            @Value("${legacy.db.dir}") String legacyDbDir,
            @Value("${parity.preconditions.dir}") String preconditionsDir) throws IOException {
        this.dataSource = dataSource;
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
}
