package naming;

import com.sun.net.httpserver.HttpServer;
import jsonhelper.ServerInfo;

import java.net.InetSocketAddress;

public class NamingServer {
    public static void main(String args[]) throws Exception  {
        if(args.length != 2){
            System.err.println("Usage: NamingServer <service-port> <registration-port>");
            System.exit(1);
        }
        int servicePort = Integer.parseInt(args[0]);
        int registrationPort = Integer.parseInt(args[1]);
        HttpServer registration = HttpServer.create(new InetSocketAddress(registrationPort),0);
        HttpServer service = HttpServer.create(new InetSocketAddress(servicePort),0);
        service.start();
        registration.start();
        System.out.println("Naming server listening on " + servicePort + " and " + registrationPort);

    }
}
