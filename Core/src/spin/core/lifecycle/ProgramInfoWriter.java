package spin.core.lifecycle;

import spin.core.util.Logger;

import java.io.*;

/**
 * A class that is responsible for writing important program information to a file for the outside world to consume.
 *
 * This is typically data required to interact with this running program, such as the port that the server is running
 * on, and is written to a file that can be uniquely identified by the PID of this process.
 */
public final class ProgramInfoWriter {
    private static final Logger LOGGER = Logger.forClass(ProgramInfoWriter.class);
    private static final long PID = ProcessHandle.current().pid();

    /**
     * Writes all of the program info to a special program info file located within the Spin data directory. If the
     * Spin data directory does not exist yet, this method creates it.
     *
     * The program info file is uniquely identified by this running process' PID.
     *
     * The program info file is marked to be deleted by the JVM upon termination since it is only a temporary file.
     *
     * @param port The port that the server is running on.
     */
    public static void publish(int port) throws IOException {
        String homeDir = System.getProperty("user.home");
        String spinDataDirPath = homeDir + File.separator + ".spin";
        String spinProgramInfoFilePath = spinDataDirPath + File.separator + "info_" + PID;

        LOGGER.log("Using Spin data directory: " + spinDataDirPath);
        LOGGER.log("Publishing to Spin program info file: " + spinProgramInfoFilePath);

        File spinDataDir = new File(spinDataDirPath);
        if (!spinDataDir.exists()) {
            if (!spinDataDir.mkdir()) {
                throw new IOException("Failed to create the Spin program data directory: " + spinDataDirPath);
            }
        }

        if (!spinDataDir.isDirectory()) {
            throw new FileNotFoundException("Spin program data directory is not a directory: " + spinDataDirPath);
        }

        File spinProgramInfoFile = new File(spinProgramInfoFilePath);
        spinProgramInfoFile.deleteOnExit();

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(spinProgramInfoFile))) {
            writer.write(String.valueOf(port));
        }
    }
}
