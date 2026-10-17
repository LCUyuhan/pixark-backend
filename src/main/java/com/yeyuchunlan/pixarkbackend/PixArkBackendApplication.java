package com.yeyuchunlan.pixarkbackend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@MapperScan("com.yeyuchunlan.pixarkbackend.mapper")
@EnableAspectJAutoProxy(exposeProxy = true)// 开启AOP
@EnableAsync

public class PixArkBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(PixArkBackendApplication.class, args);
    }

}
