package com.pfe.callmgmt.decision;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class DecisionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DecisionServiceApplication.class, args);
    }
}
