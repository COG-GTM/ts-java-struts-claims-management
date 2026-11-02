package com.northstar.claims;

import com.northstar.claims.dao.ConnectionPool;
import com.northstar.claims.util.DatabaseBootstrap;

/** Recreates the deterministic test database shared by the JDBC tests. */
public final class TestDatabase {

    public static final String PATH = "target/db/test-northstar";

    private TestDatabase() {
    }

    /** Drops pooled connections, then reloads the schema and seed rows. */
    public static void reset() throws Exception {
        System.setProperty("claims.db.path", PATH);
        ConnectionPool.getInstance().closeAll();
        DatabaseBootstrap.bootstrap(true);
    }

    /** Closes pooled connections so later bootstraps start cleanly. */
    public static void release() throws Exception {
        ConnectionPool.getInstance().closeAll();
    }
}
