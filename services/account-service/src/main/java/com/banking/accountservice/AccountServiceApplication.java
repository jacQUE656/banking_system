package com.banking.accountservice;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AccountServiceApplication {

    public static void main(String[] args)
    {
        Dotenv dotenv = Dotenv.configure()
                .directory(System.getProperty("user.dir"))
                .filename(".env")
                .ignoreIfMissing()
                .load();

        dotenv.entries().forEach(entry ->
                System.setProperty(entry.getKey(), entry.getValue())
        );
        SpringApplication.run(AccountServiceApplication.class, args);
    }

}
