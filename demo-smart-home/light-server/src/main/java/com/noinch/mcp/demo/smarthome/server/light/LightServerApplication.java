package com.noinch.mcp.demo.smarthome.server.light;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan("com.noinch.mcp.demo.smarthome")
public class LightServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(LightServerApplication.class, args);
    }

}
