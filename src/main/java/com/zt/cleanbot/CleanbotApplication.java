package com.zt.cleanbot;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.zt.cleanbot.dao")
@EnableScheduling
public class CleanbotApplication {

    public static void main(String[] args) {
        SpringApplication.run(CleanbotApplication.class, args);
    }

}
