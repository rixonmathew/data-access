package com.rixon.learn.spring.data;

import org.h2.tools.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
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

    @EventListener(org.springframework.context.event.ContextRefreshedEvent.class)
    public void start() throws java.sql.SQLException {
        this.webServer = Server.createWebServer("-webPort", String.valueOf(port), "-tcpAllowOthers").start();
        this.tcpServer = Server.createTcpServer("-tcpPort", String.valueOf(port+1), "-tcpAllowOthers").start();
    }

    @EventListener(org.springframework.context.event.ContextClosedEvent.class)
    public void stop() {
        this.webServer.stop();
        this.tcpServer.stop();
    }
}