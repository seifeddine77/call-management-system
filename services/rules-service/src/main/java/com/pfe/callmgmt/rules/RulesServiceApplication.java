package com.pfe.callmgmt.rules;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class RulesServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RulesServiceApplication.class, args);
    }
}
