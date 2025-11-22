package com.rixon.learn.spring.data.h2;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.context.annotation.Bean;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import jakarta.persistence.EntityManagerFactory;

@Configuration
@EnableJpaAuditing
@EnableAutoConfiguration
@ComponentScan(basePackages = "com.rixon.learn.spring.data.h2")
@EnableJpaRepositories(basePackages = "com.rixon.learn.spring.data.h2")
public class PersistenceTestConfig {

    // Explicitly configure JPA to scan entities in the shared "commons" module
    // so that tests find com.rixon.model.* entities.
    @Bean
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(final DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean emf = new LocalContainerEntityManagerFactoryBean();
        emf.setDataSource(dataSource);
        emf.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        java.util.Map<String, Object> jpaProps = new java.util.HashMap<>();
        jpaProps.put("hibernate.hbm2ddl.auto", "create-drop");
        jpaProps.put("hibernate.show_sql", "false");
        jpaProps.put("hibernate.format_sql", "false");
        emf.setJpaPropertyMap(jpaProps);
        emf.setPackagesToScan(
                "com.rixon.model",               // entities from commons module
                "com.rixon.learn.spring.data.h2" // any local entities if added
        );
        return emf;
    }

    @Bean
    public PlatformTransactionManager transactionManager(final EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}
