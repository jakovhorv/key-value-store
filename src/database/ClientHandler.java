package database;

import persistence.PersistenceManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class ClientHandler implements Runnable {

    private final Socket socket;
    private final KeyValueStore store;
    private final PersistenceManager persistence;

    public ClientHandler(Socket socket, KeyValueStore store, PersistenceManager persistence){
        this.socket = socket;
        this.store = store;
        this.persistence = persistence;
    }

    @Override
    public void run() {

        try (
                Socket client = socket;
                BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
                PrintWriter writer = new PrintWriter(client.getOutputStream(), true);
        ) {
            String command;
            while((command = reader.readLine()) != null){
                if(command.isBlank()){
                    writer.println("ERROR: Empty command");
                    continue;
                }
                if(command.contains("  ")){
                    writer.println("ERROR: Double spaces not allowed");
                    continue;
                }

                //System.out.println("Received: " + command);
                String[] parts = command.split(" ", 3);
                switch (parts[0].toUpperCase()) {
                    case "SET":
                        if (parts.length < 3) {
                            writer.println("ERROR: SET requires a key and a value");
                            break;
                        }

                        store.set(parts[1], parts[2]);
                        writer.println("OK");
                        break;
                    case "GET":
                        if (parts.length < 2) {
                            writer.println("ERROR: GET requires a key");
                            break;
                        }

                        String value = store.get(parts[1]);
                        writer.println(value == null ? "NULL" : value);
                        break;
                    case "DEL":
                        if (parts.length < 2) {
                            writer.println("ERROR: DEL requires a key");
                            break;
                        }

                        writer.println(
                                store.del(parts[1]) == false ? "0" : "1"
                        );
                        break;
                    case "EXPIRE":
                        if (parts.length < 3) {
                            writer.println("ERROR: EXPIRE requires a key and a value");
                            break;
                        }

                        long duration;
                        try {
                            duration = Long.parseLong(parts[2]);
                        } catch (NumberFormatException e) {
                            writer.println("ERROR: EXPIRE value must be a number");
                            break;
                        }

                        if (duration < 0) {
                            writer.println("ERROR: EXPIRE value cant be negative");
                            break;
                        }

                        try {
                            writer.println(store.expire(parts[1], duration) ? "1" : "0");
                        } catch (IllegalArgumentException e) {
                            writer.println("ERROR: EXPIRE value is too large");
                        }

                        break;

                    case "SAVE":
                        try {
                            persistence.save(store);
                            writer.println("OK");
                        } catch (IOException e) {
                            writer.println("ERROR: Could not save database");
                        }
                        break;
                    case "INCR":
                        if (parts.length != 2) {
                            writer.println("ERROR: INCR requires exactly one key");
                            break;
                        }

                        try {
                            writer.println(store.incr(parts[1]));
                        } catch (NumberFormatException e) {
                            writer.println("ERROR: Value is not a valid integer");
                        } catch (ArithmeticException e) {
                            writer.println("ERROR: Integer overflow");
                        }
                        break;

                    default:
                        writer.println("ERROR: Unknown command!");
                }
            }
        }
        catch(SocketTimeoutException e){
            System.out.println("Client disconnected due to inactivity");
        }
        catch (IOException e) {
            System.err.println("Client connection error: " + e.getMessage());
        }
    }
}
