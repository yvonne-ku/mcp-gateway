package com.noinch.mcp.demo.smarthome.server.light;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@ComponentScan("com.noinch.mcp.demo.smarthome")
@SpringBootApplication
public class LightApplication {

    public static void main(String[] args) {
        SpringApplication.run(LightApplication.class, args);
    }

}
