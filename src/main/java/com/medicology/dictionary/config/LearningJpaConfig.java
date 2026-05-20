package com.medicology.dictionary.config;

import com.medicology.dictionary.entity.UserDailyStreak;
import jakarta.persistence.EntityManagerFactory;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@EnableJpaRepositories(
        basePackages = "com.medicology.dictionary.repository.learning",
        entityManagerFactoryRef = "learningEntityManagerFactory",
        transactionManagerRef = "learningTransactionManager")
public class LearningJpaConfig {

    @Bean
    @ConfigurationProperties("learning.datasource")
    public DataSourceProperties learningDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    public DataSource learningDataSource(
            @Qualifier("learningDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean learningEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("learningDataSource") DataSource dataSource) {
        return builder
                .dataSource(dataSource)
                .packages(UserDailyStreak.class)
                .persistenceUnit("learning")
                .properties(Map.of(
                        "hibernate.hbm2ddl.auto", "none",
                        "hibernate.jdbc.time_zone", "UTC"))
                .build();
    }

    @Bean
    public PlatformTransactionManager learningTransactionManager(
            @Qualifier("learningEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}
