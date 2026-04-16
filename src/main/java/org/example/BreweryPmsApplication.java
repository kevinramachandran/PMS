package org.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BreweryPmsApplication {
    public static void main(String[] args) {
        SpringApplication.run(BreweryPmsApplication.class, args);
    }
}
