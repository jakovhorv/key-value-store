import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseServerIntegrationTest {

    @TempDir
    Path tempDir;

    Path databaseFile;

    @BeforeEach
    void setUp() {
        databaseFile = tempDir.resolve("database.dat");
    }

    @Test
    void setAndGetWorkOverTcp() throws Exception {
        Process server = new ProcessBuilder(
                "java",
                "-Ddatabase.file=" + databaseFile.toAbsolutePath(),
                "-Ddatabase.port=16379",
                "-cp",
                System.getProperty("java.class.path"),
                "database.DatabaseServer"
        ).start();

        try {
            Thread.sleep(1000);

            try (Socket socket = new Socket("localhost", 16379);
                 BufferedReader reader = new BufferedReader(
                         new InputStreamReader(socket.getInputStream()));
                 PrintWriter writer = new PrintWriter(
                         socket.getOutputStream(), true)) {

                writer.println("SET name Alice");
                assertEquals("OK", reader.readLine());

                writer.println("GET name");
                assertEquals("Alice", reader.readLine());
            }
        } finally {
            server.destroy();
            server.waitFor();
        }
    }

    @Test
    void serverTerminatesWithIdleClient() throws Exception{
        Process server = new ProcessBuilder(
                "java",
                "-Ddatabase.file=" + databaseFile.toAbsolutePath(),
                "-Ddatabase.port=16380",
                "-cp",
                System.getProperty("java.class.path"),
                "database.DatabaseServer"
        ).start();

        try{
            Thread.sleep(1100);
            try(Socket socket = new Socket("localhost", 16380)){
                server.destroy();
                assertTrue(server.waitFor(20, TimeUnit.SECONDS));
            }

        } finally{
            if (server.isAlive()) {
                server.destroyForcibly();
                server.waitFor();
            }
        }
    }

    @Test
    void overflowingEXPIREReturnsErrorButKeepsConnectionOpen() throws Exception{
        Process server = new ProcessBuilder(
                "java",
                "-Ddatabase.file=" + databaseFile.toAbsolutePath(),
                "-Ddatabase.port=16381",
                "-cp",
                System.getProperty("java.class.path"),
                "database.DatabaseServer"
        ).start();

        try {
            Thread.sleep(1000);

            try (Socket socket = new Socket("localhost", 16381);
                 BufferedReader reader = new BufferedReader(
                         new InputStreamReader(socket.getInputStream()));
                 PrintWriter writer = new PrintWriter(
                         socket.getOutputStream(), true)) {

                writer.println("SET name test");
                reader.readLine();

                writer.println("EXPIRE name " + Long.MAX_VALUE);
                assertEquals("ERROR: EXPIRE value is too large", reader.readLine());

                writer.println("GET name");
                assertEquals("test", reader.readLine());
            }
        } finally {
            server.destroy();
            server.waitFor();
        }
    }
}