package com.northstar.claims;

import com.northstar.claims.dao.ConnectionPool;
import com.northstar.claims.util.DatabaseBootstrap;

/** Rebuilds the shared HSQLDB test file and drops pooled connections to it. */
public final class TestDatabase {

    public static final String PATH = "target/db/test-northstar";

    private TestDatabase() {
    }

    public static void reset() throws Exception {
        System.setProperty("claims.db.path", PATH);
        ConnectionPool.getInstance().closeAll();
        DatabaseBootstrap.bootstrap(true);
        ConnectionPool.getInstance().closeAll();
    }

    public static void release() throws Exception {
        ConnectionPool.getInstance().closeAll();
    }
}
