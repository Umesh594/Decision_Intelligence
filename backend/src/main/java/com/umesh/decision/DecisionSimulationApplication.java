package com.umesh.decision;

import com.umesh.decision.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(exclude = {
        UserDetailsServiceAutoConfiguration.class,
        RedisRepositoriesAutoConfiguration.class
})
@EnableCaching
@EnableAsync
@EnableConfigurationProperties(AppProperties.class)
public class DecisionSimulationApplication {

    public static void main(String[] args) {
        SpringApplication.run(DecisionSimulationApplication.class, args);
    }
}
