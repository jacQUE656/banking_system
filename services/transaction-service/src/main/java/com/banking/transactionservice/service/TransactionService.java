package com.banking.transactionservice.service;

import com.banking.transactionservice.client.AccountServiceClient;
import com.banking.transactionservice.dto.TransactionRequest;
import com.banking.transactionservice.dto.TransactionResponse;
import com.banking.transactionservice.entity.Transaction;
import com.banking.transactionservice.enums.TransactionStatus;
import com.banking.transactionservice.enums.TransactionType;
import com.banking.transactionservice.event.TransactionCompletedEvent;
import com.banking.transactionservice.event.TransactionInitiatedEvent;
import com.banking.transactionservice.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionService {
    private final TransactionRepository transactionRepository;
    private final AccountServiceClient accountServiceClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RedisTemplate<String, String > redisTemplate;
    private static final String TRANSACTION_INITIATED_TOPIC = "transaction-initiated";
    private static final String TRANSACTION_COMPLETED_TOPIC = "transaction-completed";
    private static final String TRANSACTION_REFUNDED_TOPIC = "transaction-refunded";
    private static final String FRAUD_DETECTED_TOPIC = "fraud-detected";

    //HELPER METHODS

    private TransactionResponse mapToResponse(Transaction transaction) {
        return TransactionResponse.builder()
                .transactionId(transaction.getId())
                .sender(transaction.getSenderAccountNumber())
                .receiver(transaction.getReceiverAccountNumber())
                .amount(transaction.getAmount())
                .type(transaction.getType())
                .status(transaction.getStatus())
                .description(transaction.getDescription())
                .failureReason(transaction.getFailureReason())
                .referenceNumber(transaction.getReferenceNumber())
                .createdAt(transaction.getCreatedAt())
                .completedAt(transaction.getCompletedAt())
                .build();
    }




    /**
     * SAGA STEP 1 :initiate transfer
     * deducts from sender via fiegn client
     * saves transaction as PROCESSING
     * Publish event to Kafka for fraud check
     * returns
     * @param request
     * @return
     */

    public TransactionResponse createTransfer(TransactionRequest request) {

        log.info("SAGA START Transfer: {} -> {} amount: {}" ,
                request.getSenderAccountNumber(),
                request.getReceiverAccountNumber(),
                request.getAmount());

        // DEDUCT FROM SENDER
        accountServiceClient.deductBalance(
            request.getSenderAccountNumber(),
                request.getAmount()
        );

        // SET TRANSACTION TYPE AS PROCESSING AND SAVE
        Transaction transaction = Transaction.builder()
                .senderAccountNumber(request.getSenderAccountNumber())
                .receiverAccountNumber(request.getReceiverAccountNumber())
                .amount(request.getAmount())
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.PROCESSING)
                .description(request.getDescription())
                .referenceNumber(UUID.randomUUID().toString())
                .build();
        Transaction savedTransaction = transactionRepository.save(transaction);

        log.info("Transaction saved as PROCESSING: {}", savedTransaction.getId());

        //PUBLISH EVENT TO KAFKA FOR FRAUD CHECK _ WILL BE CHECKED BY FRAUD CHECK SERVICE
        TransactionInitiatedEvent event = new TransactionInitiatedEvent(
                savedTransaction.getId(),
                savedTransaction.getSenderAccountNumber(),
                savedTransaction.getReceiverAccountNumber(),
                savedTransaction.getAmount(),
                savedTransaction.getDescription()
        );

        kafkaTemplate.send(TRANSACTION_INITIATED_TOPIC, savedTransaction.getId(), event);
        log.info("SAGA STEP 2 - TransactionInitiatedEvent published: {}", savedTransaction.getId());

        //RETURN
        return  mapToResponse(savedTransaction);

    }

    public TransactionResponse getTransaction(String transactionId) {
        log.info("SAGA START GetTransaction: {}", transactionId);
        return mapToResponse(transactionRepository.
                findById(transactionId).
                orElseThrow(()-> new RuntimeException("Transaction not found")));
    }

    public List<TransactionResponse> getTransactionHistory(String accountNumber) {
        return transactionRepository
                .findBySenderAccountNumberOrderByCreatedAtDesc(accountNumber)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }


    public TransactionResponse verifyOtp(String transactionId, String otp) {
        log.info("OTP verification for transaction: {}", transactionId);
        Transaction transaction = transactionRepository.findById(transactionId).orElseThrow(()-> new RuntimeException("Transaction not found"));
        String otpKey = "verification:otp" + transactionId;
        String storedOtp = redisTemplate.opsForValue().get(otpKey);

        if (storedOtp == null){
            //OTP EXPIRED REFUND THE SENDER THE MONEY
            log.warn("OTP expired for transaction: {} refunding amount: {} back to sender", transactionId , transaction.getAmount());
            compensateTransaction(transaction , "OTP expired - transaction cancelled and amount refunded");
            return mapToResponse(transaction);
        }
        if (!storedOtp.equals(otp)){
            //BLOCK ACCOUNT AND REFUND
            log.warn("Wrong OTP for transaction: {} blocking account and refunding", transactionId);
            redisTemplate.delete(otpKey);

            blockAccountAndCompensate( transaction,
                    "Wrong OTP entered - transaction cancelled" +
                            "account blocked for security"
            );
            return mapToResponse(transaction);
        }
         log.info("OTP verified - completing transaction: {}", transactionId);
        completeTransaction(transaction);
        return mapToResponse(transaction);
    }

    private void compensateTransaction(Transaction transaction, String reason) {
        log.warn("SAGA COMPENSATION - refunding: {} amount: {}",
                transaction.getSenderAccountNumber(), transaction.getAmount());
        //CREDIT SENDER BACK USING FEIGN CLIENT
        accountServiceClient.creditBalance(
                transaction.getSenderAccountNumber(),
                transaction.getAmount()
        );
// UPDATE TRANSACTION STATUS
        transaction.setStatus(TransactionStatus.FLAGGED);
        transaction.setFailureReason(reason +
                "SAGA Compensation executed amount refunded at "+ LocalDateTime.now()
                );
        transactionRepository.save(transaction);
        //PUBLISH REFUND EVENT TO NOTIFICATION SERVICE TO ALERT USERS

        Map<String , Object> refundEvent = new HashMap<>();
        refundEvent.put("transactionId", transaction.getId());
        refundEvent.put("senderAccountNumber", transaction.getSenderAccountNumber());
        refundEvent.put("amount", transaction.getAmount());
        refundEvent.put("reason", reason);
        kafkaTemplate.send(TRANSACTION_REFUNDED_TOPIC, transaction.getId(), refundEvent);

        log.info("SAGA COMPENSATION COMPLETED - {} refunded to {}" , transaction.getAmount() , transaction.getSenderAccountNumber());
    }

    private void blockAccountAndCompensate(Transaction transaction,String reason) {
        //PUBLISH FRAUD DETECTED EVENT TO ACCOUNT SERVICE -> ACCOUNT SERVICE CONSUMES THE EVENT AND BLOCK THE ACCOUNT
        Map<String , Object> fraudEvent = new HashMap<>();
        fraudEvent.put("transactionId", transaction.getId());
        fraudEvent.put("accountNumber", transaction.getSenderAccountNumber());
        fraudEvent.put("reason", reason);
        kafkaTemplate.send(FRAUD_DETECTED_TOPIC, transaction.getSenderAccountNumber(), fraudEvent);

        log.warn("fraud.detected published - account: {} will be blocked, kindly contact the bank" , transaction.getSenderAccountNumber());

        // SAGA COMPENSATE - refund sender
        compensateTransaction(transaction, reason);
    }
private void completeTransaction(Transaction transaction) {
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setCompletedAt(LocalDateTime.now());
        transactionRepository.save(transaction);

    TransactionCompletedEvent completedEvent = new TransactionCompletedEvent(
            transaction.getId(),
            transaction.getSenderAccountNumber(),
            transaction.getReceiverAccountNumber(),
            transaction.getAmount(),
            transaction.getDescription()
    );
    kafkaTemplate.send(TRANSACTION_COMPLETED_TOPIC, transaction.getId(), completedEvent);

    log.info("SAGA COMPLETED - Transaction {} completed", transaction.getId());
}

public void processCleanResult(String transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId).orElseThrow(()-> new RuntimeException("Transactions not found"));
        if (transaction.getStatus() != TransactionStatus.PROCESSING) {
            log.warn("Transaction {} not Processing - skipping", transactionId);
        return;
        }
        completeTransaction(transaction);

}
}
