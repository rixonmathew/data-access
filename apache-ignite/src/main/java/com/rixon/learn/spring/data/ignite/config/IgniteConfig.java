package com.rixon.learn.spring.data.ignite.config;

import com.rixon.learn.spring.data.ignite.model.Person;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.Ignition;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.configuration.SqlConfiguration;
import org.apache.ignite.calcite.CalciteQueryEngineConfiguration;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

@Configuration
public class IgniteConfig {

    private static final String PERSON_CACHE = "personCache";

    @Bean
    public Ignite igniteInstance() {
        IgniteConfiguration cfg = new IgniteConfiguration();
        cfg.setLocalHost("127.0.0.1");
        
        // Configure discovery SPI
        TcpDiscoverySpi discoverySpi = new TcpDiscoverySpi();
        discoverySpi.setLocalPort(14750);
        discoverySpi.setLocalPortRange(100);
        TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();
        ipFinder.setAddresses(Arrays.asList("127.0.0.1:14750..14759"));
        discoverySpi.setIpFinder(ipFinder);
        cfg.setDiscoverySpi(discoverySpi);

        // Configure communication SPI
        org.apache.ignite.spi.communication.tcp.TcpCommunicationSpi commSpi = new org.apache.ignite.spi.communication.tcp.TcpCommunicationSpi();
        commSpi.setLocalPort(14710);
        commSpi.setLocalPortRange(100);
        cfg.setCommunicationSpi(commSpi);
        
        // Run SQL on the Calcite engine (ignite-calcite) instead of the H2 engine from ignite-indexing
        cfg.setSqlConfiguration(new SqlConfiguration()
                .setQueryEnginesConfiguration(new CalciteQueryEngineConfiguration().setDefault(true)));

        // Set client mode
        cfg.setClientMode(false);
        
        // Set node name
        cfg.setIgniteInstanceName("igniteInstance");
        
        return Ignition.start(cfg);
    }

    @Bean
    public IgniteCache<Long, Person> personCache(Ignite ignite) {
        CacheConfiguration<Long, Person> cacheConfiguration = new CacheConfiguration<>(PERSON_CACHE);
        cacheConfiguration.setIndexedTypes(Long.class, Person.class);
        return ignite.getOrCreateCache(cacheConfiguration);
    }
}
