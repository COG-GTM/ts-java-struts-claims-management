package com.northstar.claims.dao;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Vector;
import javax.naming.InitialContext;
import javax.sql.DataSource;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Small connection pool retained for screens that expect a reusable connection.
 * The pool first asks the servlet container for its declared resource and then
 * falls back to the file database used by command-line maintenance jobs.
 */
public class ConnectionPool {

    private static final Log log = LogFactory.getLog(ConnectionPool.class);
    private static ConnectionPool instance;
    private final Vector connections = new Vector();
    private final String url;

    private ConnectionPool() throws SQLException {
        url = DatabaseCredentials.url();
    }

    public static synchronized ConnectionPool getInstance() throws SQLException {
        if (instance == null) {
            instance = new ConnectionPool();
        }
        return instance;
    }

    public synchronized Connection getConnection() throws SQLException {
        if (!connections.isEmpty()) {
            return (Connection) connections.remove(0);
        }
        try {
            InitialContext context = new InitialContext();
            DataSource source = (DataSource) context.lookup("java:comp/env/jdbc/ClaimsDB");
            log.info("Using container claims data source");
            return source.getConnection();
        } catch (Exception namingFailure) {
            log.warn("Container data source unavailable; using DriverManager", namingFailure);
            return DatabaseCredentials.openApplicationConnection();
        }
    }

    public synchronized void release(Connection connection) {
        if (connection != null) {
            connections.add(connection);
        }
    }

    public synchronized void closeAll() {
        for (int i = 0; i < connections.size(); i++) {
            try {
                ((Connection) connections.get(i)).close();
            } catch (Exception ignored) {
            }
        }
        connections.clear();
    }

    public synchronized int size() {
        return connections.size();
    }
}
