package com.rixon.learn.spring.data.arrow.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "arrow")
public class ArrowProperties {

    /** Rows generated into the DuckDB {@code trades} table at startup. */
    private int sampleRows = 100_000;

    /** Rows per Arrow record batch when exporting from DuckDB. */
    private int batchSize = 8_192;

    private Server flight = new Server(47470);

    private Server flightSql = new Server(47471);

    /** Credentials both Flight servers accept (Basic auth, exchanged for a bearer token). */
    private String username = "arrow";

    private String password = "arrow";

    @Data
    public static class Server {
        private String host = "localhost";
        /** 0 picks a free port. */
        private int port;

        public Server() {
        }

        public Server(int port) {
            this.port = port;
        }
    }
}
