package benchmark;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.*;

public class DatabaseBenchmark {

    public static void main(String[] args) throws Exception {
        int clients = 10;
        int operationsPerClient = 10_000;
        long[] clientLatencies = new long[clients];

        ExecutorService executor = Executors.newFixedThreadPool(clients);
        CountDownLatch ready = new CountDownLatch(clients);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<?>[] futures = new Future<?>[clients];

            for (int i = 0; i < clients; i++) {
                final int clientId = i;

                futures[i] = executor.submit(() -> {
                    try (Socket socket = new Socket("localhost", 6379);
                         BufferedReader reader = new BufferedReader(
                                 new InputStreamReader(socket.getInputStream()));
                         PrintWriter writer = new PrintWriter(
                                 socket.getOutputStream(), true)) {

                        ready.countDown();
                        start.await();

                        long totalLatency = 0;

                        for (int j = 0; j < operationsPerClient; j++) {
                            long timePreOperation = System.nanoTime();
                            writer.println("SET client" + clientId + "_key" + j + " value" + j);


                            if (reader.readLine() == null) {
                                throw new IOException("Server disconnected");
                            }

                            totalLatency += System.nanoTime() - timePreOperation;
                        }

                        clientLatencies[clientId] = totalLatency;

                    } catch (IOException | InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                });
            }

            ready.await();

            long startTime = System.nanoTime();
            start.countDown();

            for (Future<?> future : futures) {
                future.get();
            }

            double seconds = (System.nanoTime() - startTime) / 1_000_000_000.0;
            int totalOperations = clients * operationsPerClient;

            System.out.printf("Clients: %d%n", clients);
            System.out.printf("Operations: %d%n", totalOperations);
            System.out.printf("Duration: %.3f seconds%n", seconds);
            System.out.printf("Throughput: %.0f ops/sec%n", totalOperations / seconds);
            long totalLatencies = 0;
            for(long element: clientLatencies)
                totalLatencies += element;
            double averageMs = totalLatencies / (double) totalOperations / 1_000_000;
            System.out.printf("Avg operation latency: %.3f ms%n", averageMs);



        } finally {
            executor.shutdownNow();
        }
    }
}