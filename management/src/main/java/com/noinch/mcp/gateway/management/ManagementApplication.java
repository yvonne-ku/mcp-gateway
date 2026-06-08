package com.noinch.mcp.gateway.management;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.noinch.mcp.gateway"})
public class ManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(ManagementApplication.class, args);
    }

}
