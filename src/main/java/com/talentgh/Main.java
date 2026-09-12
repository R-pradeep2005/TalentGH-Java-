package com.talentgh;

import com.sun.net.httpserver.HttpServer;
import com.talentgh.api.AnalyzeHandler;
import com.talentgh.api.RootHandler;
import com.talentgh.utils.Config;
// test
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class Main {
    public static void main(String[] args) throws IOException {
        int port = 8000;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        
        server.createContext("/", new RootHandler());
        server.createContext("/analyze", new AnalyzeHandler());
        
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        
        System.out.println("Server started on port " + port);
        System.out.println("API is running at http://localhost:" + port);
    }
}
