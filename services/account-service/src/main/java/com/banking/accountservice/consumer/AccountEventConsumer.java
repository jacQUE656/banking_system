package com.banking.accountservice.consumer;

import com.banking.accountservice.service.AccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class AccountEventConsumer {

    private final AccountService accountService;

    /**
     * Consume transaction.completed event from Kafka
     * Credits receiver amount safely from a Map payload
     *
     * @param payload
     */
    @KafkaListener(topics = "transaction.completed")
    public void consumeTransactionCompleted(
            @Payload Map<String, Object> payload
    ){
        try {
            String receiverAccount = (String) payload.get("receiverAccountNumber");
            Object rawAmount = payload.get("amount");

            if (rawAmount == null || receiverAccount == null) {
                log.error("Invalid payload received for transaction.completed: {}", payload);
                return;
            }

            // Safely convert the raw amount to BigDecimal avoiding ClassCastException
            BigDecimal creditAmount;
            if (rawAmount instanceof Number) {
                creditAmount = new BigDecimal(rawAmount.toString());
            } else if (rawAmount instanceof String) {
                creditAmount = new BigDecimal((String) rawAmount);
            } else {
                throw new IllegalArgumentException("Unsupported amount type: " + rawAmount.getClass().getName());
            }

            log.info("Crediting account for: {} amount: {}", receiverAccount, creditAmount);
            accountService.creditBalance(receiverAccount, creditAmount);

        } catch (Exception e) {
            log.error("Error while crediting balance: {}", e.getMessage(), e);
        }
    }

    /**
     * Consume fraud.detected event from Kafka
     * Blocks the flagged account
     *
     * @param payload
     */
    @KafkaListener(topics = "fraud.detected")
    public void consumeFraudDetected(@Payload Map<String, Object> payload){
        try {
            String receiverAccount = (String) payload.get("accountNumber");
            log.info("Fraud detected, blocking account for: {}", receiverAccount);
            accountService.blockAccount(receiverAccount);

        } catch (Exception e) {
            log.error("Error while blocking account: {}", e.getMessage(), e);
        }
    }
}