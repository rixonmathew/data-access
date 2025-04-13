package com.rixon.learn.spring.data.ignite.config;

import com.rixon.learn.spring.data.ignite.model.Person;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.Ignition;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
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
        
        // Configure discovery SPI
        TcpDiscoverySpi discoverySpi = new TcpDiscoverySpi();
        TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();
        ipFinder.setAddresses(Arrays.asList("127.0.0.1:47500..47509"));
        discoverySpi.setIpFinder(ipFinder);
        cfg.setDiscoverySpi(discoverySpi);
        
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
