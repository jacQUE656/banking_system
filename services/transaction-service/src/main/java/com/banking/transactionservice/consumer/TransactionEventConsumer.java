package com.banking.transactionservice.consumer;

import com.banking.transactionservice.entity.Transaction;
import com.banking.transactionservice.enums.TransactionStatus;
import com.banking.transactionservice.repository.TransactionRepository;
import com.banking.transactionservice.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionEventConsumer {
    private final TransactionRepository transactionRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final TransactionService transactionService;
    private static final long OTP_EXPIRY_MINUTES = 5;
    private static final String TRANSACTION_OTP_GENERATED_TOPIC = "transaction.otp.generated";


    @KafkaListener(topics = "verifiction.required  ")
    private void consumeVerificationRequest(@Payload Map<String, Object> payload) {

        try {
            String transactionId = (String) payload.get("transactionId");
            String accountNumber = (String) payload.get("accountNumber");
            String reason = (String) payload.get("reason");

            log.info("Verification required - transaction:{} reason:{} ", transactionId, reason);

            Transaction transaction = transactionRepository.findById(transactionId).
                    orElseThrow(()-> new RuntimeException("Transaction not found"));
            if (transaction.getStatus() != TransactionStatus.PROCESSING) {
                log.info("Transaction : {} not PROCESSING - skipping", transactionId);
                return;
            }

            //GENERATE 6 DIGIT OTP
            String otp = String.format("%06d" , (int) (Math.random() * 900000) + 100000);

            //STORE OTP IN REDIS - EXPIRES IN 5 MINUTES
            String otpKey = "verification:otp" + transactionId;
            redisTemplate.opsForValue().set(otpKey, otp , OTP_EXPIRY_MINUTES , TimeUnit.MINUTES);

            transaction.setStatus(TransactionStatus.PENDING_VERIFICATION);
            transactionRepository.save(transaction);

            log.info("OTP generated for transaction : {} expires in : {}", transactionId , OTP_EXPIRY_MINUTES);

            //NOTIFY (publish to notification service using kafka

            Map<String, Object> otpEvent = new HashMap<>();
            otpEvent.put("transactionId", transactionId);
            otpEvent.put("accountNumber", accountNumber);
            otpEvent.put("reason", reason);
            otpEvent.put("otp", otp);
            otpEvent.put("amount", payload.get("amount"));

            kafkaTemplate.send(TRANSACTION_OTP_GENERATED_TOPIC,transactionId, otpEvent);

        }catch (Exception e){
            log.error("Exception occurred while processing transaction event : {}", e.getMessage());

        }
    }

    @KafkaListener(topics = "fraud.check.clean")
    public void consumeTransactionCompleteEvent(@Payload Map<String, Object> payload) {

        try {
            String transactionId = (String) payload.get("transactionId");
            transactionService.processCleanResult(transactionId);
        }catch (Exception e){
            log.error("Exception occurred while processing fraud check result : {}" , e.getMessage());
        }
    }



}
