package spin.core.singleuse.output;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class DatabaseConnectionHolder {
    private static final String HOST = "localhost";
    private static final String PORT = "5432";
    private static final String DATABASE = "nicksdb";
    private static final String USER = "postgres";
    private static final String PASSWORD = "postgres";

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:postgresql://" + HOST + ":" + PORT + "/" + DATABASE, USER, PASSWORD);
    }

    @Override
    public String toString() {
        return this.getClass().getName() + " { database: " + DATABASE + ", host: " + HOST + ", port: " + PORT + ", user: " + USER + " }";
    }
}
