package com.northstar.claims.tests.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Base for tests that need the real HSQLDB file database in a known legacy state.
 *
 * All subclasses share one cached Spring context (identical configuration) that owns a
 * scratch file database under target/. Before each scenario the database is shut down,
 * its files are replaced with a committed legacy snapshot from parity/snapshots/, and
 * the next connection reopens it, which mirrors parity/common.py restore() without
 * restarting the application.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "claims.db.path=target/test-snapshot-db/northstar",
        // A non-pooling DataSource has no background threads that could reopen the old
        // files between SHUTDOWN and the copy; production keeps HikariCP.
        "spring.datasource.type=org.springframework.jdbc.datasource.SimpleDriverDataSource"})
public abstract class SnapshotDatabaseTest {

    /** Committed legacy pre-state snapshots, one directory per ordered scenario. */
    public static final Path SNAPSHOTS = Path.of("parity", "snapshots");

    /** Snapshot taken right after the fixed seed (login does not write), i.e. the seeded database. */
    public static final String SEEDED = "login";

    @Autowired
    protected DataSource dataSource;

    @Value("${claims.db.path}")
    private String databasePath;

    /** Replaces the live database with the legacy state captured before {@code scenario}. */
    protected void restoreSnapshot(String scenario) throws Exception {
        Path snapshot = snapshotDirectory(scenario);
        Path base = Path.of(databasePath).toAbsolutePath();
        Path directory = base.getParent();
        Files.createDirectories(directory);
        // SHUTDOWN closes the in-process database and releases its lock file.
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("SHUTDOWN");
        }
        String prefix = base.getFileName() + ".";
        try (Stream<Path> existing = Files.list(directory)) {
            for (Path path : existing.filter(p -> p.getFileName().toString().startsWith(prefix)).toList()) {
                deleteRecursively(path);
            }
        }
        // Same file filter as parity/common.py database_files(): every northstar.* except .lck.
        try (Stream<Path> files = Files.list(snapshot)) {
            for (Path file : files.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().startsWith("northstar."))
                    .filter(p -> !p.getFileName().toString().endsWith(".lck")).toList()) {
                String suffix = file.getFileName().toString().substring("northstar".length());
                Files.copy(file, directory.resolve(base.getFileName() + suffix),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    /** Resolves {@code NN_<scenario>} exactly, as parity/common.py snapshot_path() names it. */
    public static Path snapshotDirectory(String scenario) throws IOException {
        try (Stream<Path> directories = Files.list(SNAPSHOTS)) {
            List<Path> matches = directories.filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().matches("\\d{2}_" + java.util.regex.Pattern.quote(scenario)))
                    .toList();
            if (matches.size() != 1) {
                throw new IllegalStateException("Expected one snapshot for " + scenario + " in "
                        + SNAPSHOTS.toAbsolutePath() + " but found " + matches);
            }
            return matches.get(0);
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        try (Stream<Path> walk = Files.walk(path)) {
            for (Path item : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(item);
            }
        }
    }
}
