package com.banking.accountservice.service;

import com.banking.accountservice.dto.AccountResponse;
import com.banking.accountservice.dto.CreateAccountRequest;
import com.banking.accountservice.entity.Account;
import com.banking.accountservice.enums.AccountStatus;
import com.banking.accountservice.enums.AccountType;
import com.banking.accountservice.exception.AccountNotActiveException;
import com.banking.accountservice.exception.AccountNotFoundException;
import com.banking.accountservice.exception.DuplicateAccountException;
import com.banking.accountservice.exception.InsufficientBalanceException;
import com.banking.accountservice.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AccountService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final BigDecimal SAVINGS_DAILY_LIMIT = new BigDecimal("1000000");
    private static final BigDecimal DEFAULT_DAILY_LIMIT = new BigDecimal("500000");
    private static final long ACCOUNT_NUMBER_BOUND = 1_000_000_000_000L;

    private final AccountRepository accountRepository;

    // Map to account response
    private AccountResponse mapToAccountResponse(Account account) {
        AccountResponse accountResponse = new AccountResponse();
        accountResponse.setId(account.getId());
        accountResponse.setAccountNumber(account.getAccountNumber());
        accountResponse.setAccountHolderName(account.getAccountHolderName());
        accountResponse.setEmail(account.getEmail());
        accountResponse.setPhone(account.getPhone());
        accountResponse.setAccountType(account.getAccountType());
        accountResponse.setAccountStatus(account.getAccountStatus());
        accountResponse.setDailyTransactionLimit(account.getDailyTransactionLimit());
        accountResponse.setBalance(account.getBalance());
        accountResponse.setCreatedAt(account.getCreatedAt());
        return accountResponse;
    }

    // Generate 12-digit unique account number
    private String generateAccountNumber() {
        String accountNumber;
        do {
            long number = SECURE_RANDOM.nextLong(ACCOUNT_NUMBER_BOUND);
            accountNumber = String.format("%012d", number);
        } while (accountRepository.existsByAccountNumber(accountNumber));
        return accountNumber;
    }

    private Account findAccountOrThrow(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Account not found: " + accountNumber));
    }

    public AccountResponse createAccount(CreateAccountRequest request) {
        log.info("Creating account for {}", request.getEmail());

        if (accountRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateAccountException(
                    "Account already exists for email: " + request.getEmail());
        }

        BigDecimal dailyLimit = request.getAccountType() == AccountType.SAVINGS
                ? SAVINGS_DAILY_LIMIT
                : DEFAULT_DAILY_LIMIT;

        Account account = Account.builder()
                .accountHolderName(request.getAccountHolderName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .accountType(request.getAccountType())
                .accountStatus(AccountStatus.ACTIVE)
                .balance(request.getInitialDeposit())
                .accountNumber(generateAccountNumber())
                .dailyTransactionLimit(dailyLimit)
                .build();

        // Note: existsByEmail check above is a fast-path rejection only.
        // A unique constraint on `email` at the DB level is the real guard
        // against a race between two concurrent requests for the same email;
        // catch DataIntegrityViolationException here and rethrow as
        // DuplicateAccountException once that constraint is in place.
        Account createdAccount = accountRepository.save(account);
        log.info("Created account {}", createdAccount.getAccountNumber());
        return mapToAccountResponse(createdAccount);
    }

    public AccountResponse getAccount(String accountNumber) {
        log.info("Getting account for {}", accountNumber);
        Account account = findAccountOrThrow(accountNumber);
        return mapToAccountResponse(account);
    }

    /**
     * @param accountNumber account to look up
     * @return current balance
     */
    public BigDecimal getBalance(String accountNumber) {
        log.info("Getting balance for {}", accountNumber);
        Account account = findAccountOrThrow(accountNumber);
        return account.getBalance();
    }

    /**
     * Block account - called by fraud detection service.
     *
     * @param accountNumber account to block
     */
    public void blockAccount(String accountNumber) {
        log.info("Blocking account for {}", accountNumber);
        Account account = findAccountOrThrow(accountNumber);
        account.setAccountStatus(AccountStatus.BLOCKED);
        accountRepository.save(account);
        log.info("Blocked account {}", account.getAccountNumber());
    }

    /**
     * Deduct balance from sender account - called by transaction service.
     *
     * @param accountNumber account to deduct from
     * @param deductAmount  amount to deduct
     */
    public void deductBalance(String accountNumber, BigDecimal deductAmount) {
        log.info("Deducting from account {}", accountNumber);
        Account account = findAccountOrThrow(accountNumber);

        if (account.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException(
                    "Account is not active: " + accountNumber);
        }
        if (account.getBalance().compareTo(deductAmount) < 0) {
            throw new InsufficientBalanceException(
                    "Insufficient balance in account: " + accountNumber);
        }

        account.setBalance(account.getBalance().subtract(deductAmount));
        accountRepository.save(account);
        log.info("Deducted from account {}", account.getAccountNumber());
    }

    /**
     * Credit balance to receiver account - called by transaction service.
     *
     * @param accountNumber account to credit
     * @param creditAmount  amount to credit
     */
    public void creditBalance(String accountNumber, BigDecimal creditAmount) {
        log.info("Crediting account {}", accountNumber);
        Account account = findAccountOrThrow(accountNumber);
        account.setBalance(account.getBalance().add(creditAmount));
        accountRepository.save(account);
        log.info("Credited account {}", account.getAccountNumber());
    }

    public List<AccountResponse> getAllAccounts(){
        List<Account> accounts = accountRepository.findAll();
        if (accounts.isEmpty()) {
            throw new AccountNotFoundException("No accounts found");
        }
        return accounts.stream().map(this::mapToAccountResponse).collect(Collectors.toList());
    }

    public String getAccountEmail(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(()-> new AccountNotFoundException(accountNumber));
        return account.getEmail();
    }
}