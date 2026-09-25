package com.banking.frauddetectionservice.service;

import com.banking.frauddetectionservice.client.AccountServiceFeignClient;
import com.banking.frauddetectionservice.results.FraudCheckResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class FraudDetectionService {

    private final AccountServiceFeignClient accountFeignClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${fraud.max-transactions-per-minute}")
    private int maxTransactionPerMinute;

    @Value("${fraud.suspicious-amount-multiplier}")
    private int suspiciousAmountMultiplier;

    @Value("${fraud.max-balance-percentage}")
    private double maxBalancePercentage;

    // ALL RESULTS ARE GOING TO BE SENT TO THE TRANSACTION EVENT COSUMER IN TRANSACTION SERVICE USING KAFKA

    private final String VERIFICATION_REQUIRED_TOPIC = "verification.required";
    private  final String FRAUD_CHECK_CLEAN_RESULT_TOPIC = "fraud.check.clean";

    /**
     * Performs check for all transaction using redis and then publish the results via kafka
     * @param payload
     */
    public void checkTransaction(Map<String, Object> payload) {
        String transactionId = (String) payload.get("transactionId");
        String accountNumber = (String) payload.get("senderAccountNumber");
        BigDecimal amount = (BigDecimal) payload.get("amount");

        BigDecimal senderBalance = accountFeignClient.getAccountBalance(accountNumber);

        log.info("Checking transactions: {} account :: {} balance: {}", transactionId, senderBalance, amount);

        FraudCheckResult result = performFraudCheck(accountNumber,amount,senderBalance);

        if(result.isFraud()) {
            log.info("Suspicious activity detected - account : {}" + "reason: {} requesting OTP verification", accountNumber, result.getReason());

            Map<String, Object> verificationEvent = new HashMap<>();
            verificationEvent.put("transactionId", transactionId);
            verificationEvent.put("accountNumber", accountNumber);
            verificationEvent.put("amount", amount);
            verificationEvent.put("reason", result.getReason());

            //PUBLISH THAT USER NEED S VERIFICATION USING KAFKA
            kafkaTemplate.send(VERIFICATION_REQUIRED_TOPIC, transactionId, verificationEvent);
        }else {
            log.info("Transaction is clean");

            Map<String, Object> transactionEvent = new HashMap<>();
            transactionEvent.put("transactionId", transactionId);
            transactionEvent.put("isFraud", false);
            transactionEvent.put("reason", null);

            //PUBLISH THAT TRANSACTION IS CLEAN
              kafkaTemplate.send(FRAUD_CHECK_CLEAN_RESULT_TOPIC , transactionId, transactionEvent);
        }
    }


private FraudCheckResult performFraudCheck(String accountNumber, BigDecimal amount, BigDecimal senderBalance) {

        // CHECK THE VELOCITY OF THE TRANSACTION (amount of transaction in 60s)
    if (isVelocityExceeded(accountNumber)){
        return new FraudCheckResult(
                true , "Too many transaction under 60 seconds"
        );
    }

    // CHECK IF THE AMOUNT TRANSFERRED IS LARGER THAN THE AVERAGE AMOUNT THE USER NORMALLY TRANSFER
    if (isAmountSuspicious(accountNumber, amount)){
        return new FraudCheckResult(
                true , "Unusual transaction amount " + "- exceeds 3x your average transaction amount"
        );
    }
    // CHECK IF TRANSACTION AMOUNT IS LARGER THAN 90% OF THE USER BALANCE
    if (senderBalance.compareTo(BigDecimal.ZERO)> 0
            && isBalanceCheckFailed(senderBalance, amount)){
        return new FraudCheckResult(
                true , "Transaction exceed 90% of account balance"
        );
    }
    return new FraudCheckResult(false , null);
}

private boolean isVelocityExceeded(String accountNumber) {
    String key = "fraud:velocity" + accountNumber;
    Long count = redisTemplate.opsForValue().increment(key);

    if (count != null && count == 1) {
        redisTemplate.expire(key, 60, TimeUnit.SECONDS);
    }
        log.info("Velocity check - account : {} count : {}/{}", accountNumber, count, maxTransactionPerMinute);

        return count != null && count > maxTransactionPerMinute;
}

private boolean isAmountSuspicious(String accountNumber, BigDecimal amount) {
        // FIRST FIND THE AVERAGE AMOUNT THE USER SENDS
    String avgKey = "fraud:avg_amount" + accountNumber;
    String avgStr = redisTemplate.opsForValue().get(avgKey);

    if (avgStr==null) {
        redisTemplate.opsForValue().set(avgKey , amount.toString());
        return false;
    }

    BigDecimal avgAmount = new BigDecimal(avgStr);
    BigDecimal threshold = avgAmount.multiply(
            BigDecimal.valueOf(suspiciousAmountMultiplier)
    );

    //UPDATE RUNNING AVERAGE

    BigDecimal newAvg = avgAmount.add(amount)
            .divide(BigDecimal.valueOf(2), RoundingMode.HALF_UP);

    redisTemplate.opsForValue().set(avgKey , newAvg.toString());
    log.info("Amount checked - amount : {} threshold : {} suspicious : {} ", newAvg , threshold , amount.compareTo(threshold) > 0);
return amount.compareTo(threshold) > 0;
}

private boolean isBalanceCheckFailed(BigDecimal senderBalance, BigDecimal amount) {
     BigDecimal maxAllowedBalance = senderBalance.multiply(
             BigDecimal.valueOf(maxBalancePercentage)
     );

     log.info("Balance check - amount : {} maxAllowed : {} suspicious : {}", amount, maxAllowedBalance , amount.compareTo(maxAllowedBalance) > 0  );
     return amount.compareTo(maxAllowedBalance) > 0;
}

}
