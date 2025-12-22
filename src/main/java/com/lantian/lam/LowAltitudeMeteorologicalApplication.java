package com.lantian.lam;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.lantian.lam.mapper")
@SpringBootApplication
public class LowAltitudeMeteorologicalApplication {

    public static void main(String[] args) {
        SpringApplication.run(LowAltitudeMeteorologicalApplication.class, args);
    }

}
