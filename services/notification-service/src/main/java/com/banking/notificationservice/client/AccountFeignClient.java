package com.banking.notificationservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "account-service")
public interface AccountFeignClient {

    @GetMapping("/api/v1/accounts/{accountNumber}/email")
    String getAccountEmail(@PathVariable String accountNumber);
}