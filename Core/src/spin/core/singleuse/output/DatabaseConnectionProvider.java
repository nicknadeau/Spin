package spin.core.singleuse.output;

import java.io.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * A class that provides connections to a postgres database.
 */
public final class DatabaseConnectionProvider {
    private static final String HOST_KEY = "host=";
    private static final String PORT_KEY = "port=";
    private static final String DATABASE_KEY = "database=";
    private static final String USER_KEY = "user=";
    private static final String PASSWORD_KEY = "password=";
    private static final String COMMENT_SYMBOL = "#";
    private String url = null;
    private String user = null;
    private String password = null;
    private boolean isInitialized = false;

    /**
     * Initializes this database connection provider using the given database configuration file.
     *
     * The provided file is expected to be a plain text UTF-8 file with the following format:
     *
     * host=<host>
     * port=<port>
     * database=<database>
     * user=<user>
     * password=<password>
     *
     * Ordering does not matter but all 5 key-value pairs must be present.
     *
     * @param file The database configuration file.
     */
    public void initialize(File file) throws IOException {
        if (this.isInitialized) {
            throw new IllegalArgumentException("Cannot initialize: this provider has already been initialized.");
        }

        String host = null;
        String port = null;
        String database = null;
        String user = null;
        String password = null;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line = reader.readLine();
            while (line != null) {
                if (line.startsWith(COMMENT_SYMBOL) || line.isEmpty()) {
                    // This is a valid character but it is a comment line so we ignore it.
                } else if (line.startsWith(HOST_KEY)) {
                    host = line.substring(HOST_KEY.length());
                } else if (line.startsWith(PORT_KEY)) {
                    port = line.substring(PORT_KEY.length());
                } else if (line.startsWith(DATABASE_KEY)) {
                    database = line.substring(DATABASE_KEY.length());
                } else if (line.startsWith(USER_KEY)) {
                    user = line.substring(USER_KEY.length());
                } else if (line.startsWith(PASSWORD_KEY)) {
                    password = line.substring(PASSWORD_KEY.length());
                } else {
                    throw new IOException("Unsupported key-value pairing: " + line);
                }
                line = reader.readLine();
            }
        }

        if (host == null || port == null || database == null || user == null || password == null) {
            throw new IllegalArgumentException("Missing at least one database config property. Required properties: 'host', 'port', 'database', 'user', 'password'");
        }
        this.url = "jdbc:postgresql://" + host + ":" + port + "/" + database;
        this.user = user;
        this.password = password;
        this.isInitialized = true;
    }

    /**
     * Returns a new connection to the database.
     *
     * @return a new connection.
     */
    public Connection getConnection() throws SQLException {
        if (!this.isInitialized) {
            throw new IllegalStateException("Cannot get connection: this provider must first be initialized.");
        }
        return DriverManager.getConnection(this.url, this.user, this.password);
    }

    @Override
    public String toString() {
        return this.getClass().getName() + ((this.isInitialized) ? " { url: " + this.url + ", user: " + this.user + " }" : " { uninitialized }");
    }
}
