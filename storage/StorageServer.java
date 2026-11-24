package storage;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class StorageServer {
    private final File root;
    private final int clientPort;
    private final int commandPort;
    private final HttpServer clientServer;
    private final HttpServer commandServer;
    private final StorageHandlers handlers;

    public StorageServer(File root, int clientPort, int commandPort) throws IOException {
        this.root = root;
        this.clientPort = clientPort;
        this.commandPort = commandPort;
        this.handlers = new StorageHandlers(root);
        this.clientServer = HttpServer.create(new InetSocketAddress(clientPort), 0);
        this.commandServer = HttpServer.create(new InetSocketAddress(commandPort), 0);
        this.clientServer.setExecutor(Executors.newCachedThreadPool());
        this.commandServer.setExecutor(Executors.newCachedThreadPool());

        registerHandlers();
    }

    private void registerHandlers() {
        clientServer.createContext("/storage_size", handlers.size());
        clientServer.createContext("/storage_read", handlers.read());
        clientServer.createContext("/storage_write", handlers.write());
        commandServer.createContext("/storage_create", handlers.create());
        commandServer.createContext("/storage_delete", handlers.delete());
        commandServer.createContext("/storage_copy", notImplemented());
    }

    private static HttpHandler notImplemented() {
        return exchange -> {
            exchange.sendResponseHeaders(501, -1);
            exchange.close();
        };
    }

    private void start() {
        clientServer.start();
        commandServer.start();
    }

    public void stop() {
        clientServer.stop(0);
        commandServer.stop(0);
    }

    /**
     * Registers with the naming server. Returns false if already registered.
     */
    public boolean register(int namingRegistrationPort) throws IOException {
        return Registration.register(root, "localhost", clientPort, commandPort,
                namingRegistrationPort);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            System.err.println("Usage: StorageServer <client-port> <command-port> <naming-registration-port> <root-dir>");
            System.exit(1);
        }

        int clientPort = Integer.parseInt(args[0]);
        int commandPort = Integer.parseInt(args[1]);
        int namingRegistrationPort = Integer.parseInt(args[2]);
        //gestion files and directories
        File root = new File(args[3]);
        if (!root.exists() && !root.mkdirs() && !root.isDirectory()) {
            System.err.println("Error: root directory couldn't be created");
            System.exit(1);
        }
        StorageServer server = new StorageServer(root, clientPort, commandPort);
        server.start();
        System.out.println("Storage server listening on " + clientPort);
        if (!server.register(namingRegistrationPort)) {
            System.err.println("Already registered with the naming server, exiting.");
            server.stop();
            System.exit(1);
        }
    }
}



