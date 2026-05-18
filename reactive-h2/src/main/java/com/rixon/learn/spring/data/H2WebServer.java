package com.rixon.learn.spring.data;

import org.h2.tools.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;


/**
 * With webflux the default properties will not show the web-console
 */
@Component
public class H2WebServer {

    @Value("${h2.port:29596}")
    private int port;
    private Server webServer;
    private Server tcpServer;
    private boolean started = false;

    @EventListener(org.springframework.context.event.ContextRefreshedEvent.class)
    public synchronized void start() throws java.sql.SQLException {
        if (started) {
            return;
        }
        this.webServer = Server.createWebServer("-webPort", String.valueOf(port), "-tcpAllowOthers").start();
        int tcpPort = port == 0 ? 0 : port + 1;
        this.tcpServer = Server.createTcpServer("-tcpPort", String.valueOf(tcpPort), "-tcpAllowOthers").start();
        started = true;
    }

    @EventListener(org.springframework.context.event.ContextClosedEvent.class)
    public synchronized void stop() {
        if (this.webServer != null) {
            this.webServer.stop();
            this.webServer = null;
        }
        if (this.tcpServer != null) {
            this.tcpServer.stop();
            this.tcpServer = null;
        }
        started = false;
    }
}