package com.datapeice.neviim;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class NeviimApplication {

    public static void main(String[] args) {
        SpringApplication.run(NeviimApplication.class, args);
    }

}
