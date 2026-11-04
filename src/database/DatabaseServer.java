package database;

import persistence.PersistenceManager;
import scheduler.AutoSaveScheduler;
import scheduler.ExpirationCleaner;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.*;

public class DatabaseServer {


    public static void main(String[] args) throws IOException {

        int port = Integer.getInteger("database.port", 6379);
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("Server listening on port " + port);

        Set<Socket> clientSockets = ConcurrentHashMap.newKeySet();

        ExecutorService executor = new ThreadPoolExecutor(
                10,
                10,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(50)
        );

        KeyValueStore store = new KeyValueStore();

        Path databasePath = Path.of(
                System.getProperty("database.file", "database.dat")
        );

        PersistenceManager persistence = new PersistenceManager(databasePath);

        if (Files.exists(databasePath)) {
            persistence.load(store);
        }

        ExpirationCleaner cleaner = new ExpirationCleaner(store);
        cleaner.start();

        AutoSaveScheduler autoSave = new AutoSaveScheduler(store, persistence);
        autoSave.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                serverSocket.close();
                executor.shutdown();

                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    for (Socket socket : clientSockets) {
                        try {
                            socket.close();
                        } catch (IOException e) {
                            System.err.println(e.getMessage());
                        }
                    }

                    executor.shutdownNow();

                    if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                        System.err.println("Some client handlers did not terminate.");
                    }
                }
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                System.err.println("Shutdown error: " + e.getMessage());
            } finally {
                try {
                    cleaner.stop();
                    autoSave.stop();
                    persistence.save(store);
                } catch (IOException | InterruptedException | IllegalStateException e) {
                    System.err.println("Final cleanup failed: " + e.getMessage());
                }
            }
        }));

        while (!serverSocket.isClosed()) {
            try {
                Socket clientSocket = serverSocket.accept();
                clientSockets.add(clientSocket);
                clientSocket.setSoTimeout(30_000);

                try {
                    executor.execute(() -> {
                        try {
                            new ClientHandler(clientSocket, store, persistence).run();
                        } finally {
                            clientSockets.remove(clientSocket);
                        }
                    });
                } catch (RejectedExecutionException e) {
                    clientSockets.remove(clientSocket);
                    clientSocket.close();
                }
            } catch (SocketException e) {
                if (!serverSocket.isClosed()) {
                    throw e;
                }
            }
        }

    }

}