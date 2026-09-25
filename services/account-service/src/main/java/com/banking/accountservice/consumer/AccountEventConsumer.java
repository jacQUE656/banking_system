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

    //Here we are consuming transaction completed and fraud event from kafka

    /**
     * Consume transaction.completed event from Kafka
     * Credits receiver amount
     * @param payload
     */

    @KafkaListener(topics = "transaction.completed")
    public void consumeTransactionCompleted(
            @Payload Map<String , Object> payload
            ){
        try {
            String receiverAccount = (String)  payload.get("accountNumber");
            BigDecimal creditAmount = (BigDecimal) payload.get("creditAmount");

            log.info("Crediting account for: {} amount: {}", receiverAccount ,  creditAmount);
            accountService.creditBalance(receiverAccount, creditAmount);

        }catch (Exception e){
            log.error("Error while credit balance : {}", e.getMessage());
        }

    }

    /**
     * consume fraud.detected event from Kafka
     * Blocks the flagged account
     * @param payload
     */

    @KafkaListener(topics = "fraud.detected")
    public void consumeFraudDetected(@Payload Map<String , Object> payload){
        try {
            String receiverAccount = (String)  payload.get("accountNumber");
            log.info("Fraud detected, blocking account for: {}", receiverAccount);
            accountService.blockAccount(receiverAccount);



        }catch (Exception e){
            log.error("Error while blocking balance : {}", e.getMessage());
        }
    }


}
