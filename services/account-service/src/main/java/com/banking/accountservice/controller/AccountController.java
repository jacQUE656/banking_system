package com.banking.accountservice.controller;

import com.banking.accountservice.dto.AccountResponse;
import com.banking.accountservice.dto.AmountRequest;
import com.banking.accountservice.dto.CreateAccountRequest;
import com.banking.accountservice.dto.OperationResponse;
import com.banking.accountservice.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
@Slf4j
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping
    public ResponseEntity<List<AccountResponse>> getAllAccounts() {
        log.info("Get all accounts");
        return ResponseEntity.ok(accountService.getAllAccounts());
    }

    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(
            @Valid @RequestBody CreateAccountRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.createAccount(request));
    }

    @GetMapping("/{accountNumber}")
    public ResponseEntity<AccountResponse> getAccount(
            @PathVariable String accountNumber
    ) {
        return ResponseEntity.ok(accountService.getAccount(accountNumber));
    }

    @GetMapping("/{accountNumber}/balance")
    public ResponseEntity<BigDecimal> getAccountBalance(
            @PathVariable String accountNumber
    ) {
        return ResponseEntity.ok(accountService.getBalance(accountNumber));
    }

    @GetMapping("/{accountNumber}/email")
    public ResponseEntity<String> getAccountEmail(@PathVariable String accountNumber) {
        return ResponseEntity.ok(accountService.getAccountEmail(accountNumber));
    }

    @PutMapping("/{accountNumber}/block")
    public ResponseEntity<OperationResponse> blockAccount(
            @PathVariable String accountNumber
    ) {
        accountService.blockAccount(accountNumber);
        return ResponseEntity.ok(OperationResponse.success("Account blocked successfully"));
    }

    /**
     * SAGA STEP 1 - Deduct Balance
     * Called by transaction service when transfer is initiated.
     */
    @PutMapping("/{accountNumber}/deduct")
    public ResponseEntity<OperationResponse> deductBalance(
            @PathVariable String accountNumber,
            @Valid @RequestBody AmountRequest request
    ) {
        accountService.deductBalance(accountNumber, request.getAmount());
        return ResponseEntity.ok(OperationResponse.success("Account deducted successfully"));
    }

    /**
     * SAGA STEP 2 - Compensating transaction endpoint.
     * Called by transaction service in two scenarios:
     * 1. Fraud detected -> refund sender (undo step 1)
     * 2. Transaction completed -> credit receiver
     *
     * See NOTE on deductBalance above re: internal-only access.
     */
    @PutMapping("/{accountNumber}/credit")
    public ResponseEntity<OperationResponse> creditBalance(
            @PathVariable String accountNumber,
            @Valid @RequestBody AmountRequest request
    ) {
        accountService.creditBalance(accountNumber, request.getAmount());
        return ResponseEntity.ok(OperationResponse.success("Account credited successfully"));
    }
}