package com.noinch.mcp.demo.smarthome.server.lock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan("com.noinch.mcp.demo.smarthome")
public class LockServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(LockServerApplication.class, args);
    }

}