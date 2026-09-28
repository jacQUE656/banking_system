package com.banking.frauddetectionservice.client;


import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.math.BigDecimal;

@FeignClient(name = "account-service")
public interface AccountServiceFeignClient {

    @GetMapping("/api/v1/accounts/{accountNumber}/balance")
    BigDecimal getAccountBalance(
            @PathVariable String accountNumber);
}
