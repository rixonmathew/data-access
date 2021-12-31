package com.rixon.learn.spring.data;

import org.h2.tools.Server;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;


/**
 * With webflux the default properties will not show the web-console
 */
@Component
public class H2WebServer {
    private Server webServer;
    private Server tcpServer;

    @EventListener(org.springframework.context.event.ContextRefreshedEvent.class)
    public void start() throws java.sql.SQLException {
        this.webServer = Server.createWebServer("-webPort", "29596", "-tcpAllowOthers").start();
        this.tcpServer = Server.createTcpServer("-tcpPort", "29597", "-tcpAllowOthers").start();
    }

    @EventListener(org.springframework.context.event.ContextClosedEvent.class)
    public void stop() {
        this.webServer.stop();
        this.tcpServer.stop();
    }
}