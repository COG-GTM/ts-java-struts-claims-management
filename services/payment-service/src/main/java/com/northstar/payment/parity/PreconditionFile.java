package com.northstar.payment.parity;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads parity/preconditions/payment.sql: sections headed
 * {@code -- scenario: <name>}, each holding zero or more statements, one per
 * line, ending in {@code ;}. Other comment lines and blank lines are ignored.
 */
final class PreconditionFile {
    private static final String HEAD = "-- scenario: ";

    private PreconditionFile() {
    }

    /** The statements of one section, or null when the file has no such section. */
    static List<String> section(String text, String scenario) {
        List<String> statements = null;
        for (String raw : text.split("\n")) {
            String line = raw.strip();
            if (line.startsWith(HEAD)) {
                if (statements != null) {
                    return statements;
                }
                if (line.substring(HEAD.length()).strip().equals(scenario)) {
                    statements = new ArrayList<>();
                }
            } else if (statements != null && !line.isEmpty() && !line.startsWith("--")) {
                statements.add(line.endsWith(";") ? line.substring(0, line.length() - 1) : line);
            }
        }
        return statements;
    }
}
