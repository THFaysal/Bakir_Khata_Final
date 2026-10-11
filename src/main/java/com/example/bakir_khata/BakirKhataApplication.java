package com.example.bakir_khata;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class BakirKhataApplication {

    public static void main(String[] args) {
        SpringApplication.run(BakirKhataApplication.class, args);
    }

}
