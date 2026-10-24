package edu.campus.green;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GreenCampusApplication {
    public static void main(String[] args) {
        SpringApplication.run(GreenCampusApplication.class, args);
    }
}
