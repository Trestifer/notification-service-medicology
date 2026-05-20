package com.medicology.dictionary.config;

import com.medicology.dictionary.entity.Notification;
import com.medicology.dictionary.entity.NotificationDelivery;
import com.medicology.dictionary.entity.NotificationPreference;
import jakarta.persistence.EntityManagerFactory;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
        basePackages = "com.medicology.dictionary.repository.notification",
        entityManagerFactoryRef = "notificationEntityManagerFactory",
        transactionManagerRef = "notificationTransactionManager")
public class NotificationJpaConfig {

    @Bean
    @Primary
    @ConfigurationProperties("notification.datasource")
    public DataSourceProperties notificationDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    public DataSource notificationDataSource(
            @Qualifier("notificationDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean
    @Primary
    public LocalContainerEntityManagerFactoryBean notificationEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("notificationDataSource") DataSource dataSource) {
        return builder
                .dataSource(dataSource)
                .packages(Notification.class, NotificationDelivery.class, NotificationPreference.class)
                .persistenceUnit("notification")
                .properties(Map.of(
                        "hibernate.hbm2ddl.auto", "update",
                        "hibernate.jdbc.time_zone", "UTC"))
                .build();
    }

    @Bean
    @Primary
    public PlatformTransactionManager notificationTransactionManager(
            @Qualifier("notificationEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}
