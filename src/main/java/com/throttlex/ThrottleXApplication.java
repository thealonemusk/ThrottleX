package com.throttlex;

import com.throttlex.config.ThrottleXProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(ThrottleXProperties.class)
public class ThrottleXApplication {

    public static void main(String[] args) {
        SpringApplication.run(ThrottleXApplication.class, args);
    }

}
