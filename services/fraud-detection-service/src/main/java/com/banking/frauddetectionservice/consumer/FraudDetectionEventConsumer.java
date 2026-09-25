package com.banking.frauddetectionservice.consumer;

import com.banking.frauddetectionservice.service.FraudDetectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class FraudDetectionEventConsumer {
    private final FraudDetectionService  service;

    /**
     * Listens to transaction initiated topic coming from the transaction service via kafka
     * Every transaction goes through fraud check before completing
     * @param payload
     */
    @KafkaListener(topics = "transaction.initiated", groupId = "fraud-detection-group ")
    public void consumeTransactionInitiated(
            @Payload Map<String , Object> payload
            ){

        log.info("Received transaction for fraud check: {}" , payload.get("transactionId"));

        try {

            service.checkTransaction(payload);

        }catch (Exception ex){
        log.error("Error checking for fraud for transaction {}" , payload.get("transactionId"));
        }
    }
}
