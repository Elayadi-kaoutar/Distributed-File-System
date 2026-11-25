package naming;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import commun.HttpUtil;
import jsonhelper.FilesReturn;
import jsonhelper.RegisterRequest;
import jsonhelper.ServerInfo;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.Executors;

public class NamingServer {
    private final HttpServer serviceServer;
    private final HttpServer registryServer;
    private final  FileTree tree;
    NamingServer(int servicePort,int registerationPort)throws IOException {
        serviceServer = HttpServer.create(new InetSocketAddress(servicePort), 0);
        registryServer = HttpServer.create(new InetSocketAddress(registerationPort), 0);
        this.serviceServer.setExecutor(Executors.newCachedThreadPool());
        this.registryServer.setExecutor(Executors.newCachedThreadPool());
        this.tree = new FileTree();
        registerHandlers();
    }
   private void registerHandlers(){
       registryServer.createContext("/register",register());
   }
    private HttpHandler register() {
        return (HttpExchange exchange) -> {
            try {
                if (!HttpUtil.requirePost(exchange)) {
                    return;
                }
                RegisterRequest req = HttpUtil.readJson(exchange, RegisterRequest.class);
                if (req == null || req.storage_ip == null || req.storage_ip.isBlank()
                        || req.client_port <= 0 || req.command_port <= 0 || req.files == null) {
                    HttpUtil.sendBadRequest(exchange);
                    return;
                }
                StorageInfo server = new StorageInfo(req.storage_ip, req.client_port,
                        req.command_port);
                try {
                    List<String> duplicates = tree.register(server, req.files);
                    HttpUtil.sendJson(exchange, 200,
                            new FilesReturn(duplicates.toArray(new String[0])));
                } catch (IllegalStateException e) {
                    HttpUtil.sendError(exchange, 409, "IllegalStateException", e.getMessage());
                }
            } catch (Exception e) {
                try {
                    HttpUtil.sendError(exchange, 500, "IOException", String.valueOf(e));
                } catch (Exception ignored) {
                    // response already started, nothing more to do
                }
            } finally {
                exchange.close();
            }
        };
    }
    public void start(){
        serviceServer.start();
        registryServer.start();
    }


    public static void main(String args[]) throws Exception  {
        if(args.length != 2){
            System.err.println("Usage: NamingServer <service-port> <registration-port>");
            System.exit(1);
        }
        int servicePort = Integer.parseInt(args[0]);
        int registrationPort = Integer.parseInt(args[1]);
        NamingServer server = new NamingServer(servicePort,registrationPort);
        server.start();
        System.out.println("Naming server listening on " + servicePort + " and " + registrationPort);

    }
}
