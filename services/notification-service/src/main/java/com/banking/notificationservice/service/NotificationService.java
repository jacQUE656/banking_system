package com.banking.notificationservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
public class NotificationService {


    @KafkaListener(topics = "transaction.otp.generated")
    public void consumeOtpGenerated(
            @Payload Map<String , Object> payload
            ){
        try {
            String accountNumber = (String) payload.get("accountNumber");
            String transactionId = (String) payload.get("transactionId");
            String reason = (String) payload.get("reason");
            String otp = (String) payload.get("otp");
            String amount = (String) payload.get("amount");

            sendAlert( accountNumber,
                    "TRANSACTION VERIFICATION REQUIRED",
                    String.format(
                            "Suspicious activity detected on your account" +
                                    "Reason: %s" +
                                    "A transaction of %s is pending verification" +
                                    "your otp is : %s. Valid for 5 minutes " +
                                    "If this wasn't you - ignore this message",
                            reason,amount,otp
                    )
            );

        }catch (Exception e){
            log.error("Error sending OTP notification", e.getMessage());
        }
    }

    @KafkaListener(topics = "transaction.completed")
    public void consumeTransactionCompleted(
            @Payload Map<String , Object> payload){
    try {
        String senderAccountNumber = (String) payload.get("senderAccountNumber");
        String receiverAccountNumber = (String) payload.get("receiverAccountNumber");
        String amount = (String) payload.get("amount");

        // DEBIT ALERT
        sendAlert(senderAccountNumber ,
                "DEBIT ALERT" ,
                String.format(
                        "%s debited from account %a",
                        amount , senderAccountNumber
                )
                );

        //CREDIT ALERT

        sendAlert(receiverAccountNumber ,
                "CREDIT ALERT" ,
                String.format(
                        "%s credited to account %a",
                        amount , receiverAccountNumber
                )
        );
    }   catch (Exception e){
        log.error("Error sending transaction notification : {}", e.getMessage());
    }

    }

    @KafkaListener(topics = "fraud.detected")
    public void consumeFraudDetected(
            @Payload Map<String , Object> payload){
        try {
            String accountNumber = (String) payload.get("accountNumber");
            String reason = (String) payload.get("reason");

            sendAlert(
                    accountNumber,
                    "SUSPICIOUS ACTIVITY DETECTED",
                    String.format(
                            "Your account %s has been blocked" +
                                    "Reason : %s. " +
                                    "Please contact your bank immediately",
                            accountNumber, reason
                    )
            );
        }catch (Exception e){
            log.error("Error sending fraud alert: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "transaction.refunded")
    public void consumeTransactionRefunded(
            @Payload Map<String , Object> payload){
        try {
            String senderAccountNumber = (String) payload.get("senderAccountNumber");
            String amount = (String) payload.get("amount").toString();
            String reason = (String) payload.get("reason");

            sendAlert(
                    senderAccountNumber,
                    "REFUND PROCESSED",
                    String.format(
                            "Your transaction of %s has been refunded" +
                                    "Reason : %s. " +
                                    "%s has been refunded to your account %s",
                            amount , reason, amount , senderAccountNumber
                    )
            );
        }catch (Exception e){
            log.error("Error sending refund transaction notification: {}", e.getMessage());
              }
    }

    @KafkaListener(topics = "payment.completed")
    public void consumePaymentCompleted(Map<String , Object> payload){
        try {

            String accountNumber = (String) payload.get("accountNumber");
            String amount = (String) payload.get("amount").toString();

            sendAlert(
                    accountNumber,
                    "PAYMENT SUCCESSFUL",
                    String.format(
                            "Payment of %s completed" +
                                    "Razorpay ID %s",
                            amount , payload.get("razorpayPaymentId")
                    )
            );
        }
        catch (Exception e){
            log.error("Error sending payment completed notification: {}", e.getMessage());
        }
    }
    @KafkaListener(topics = "payment.failed")
    public void paymentFailed(
            @Payload Map<String , Object> payload){
        try {
            String accountNumber = (String) payload.get("accountNumber");
            String amount = (String) payload.get("amount").toString();
            sendAlert(
                    accountNumber,
                    "PAYMENT FAILED",
                    String.format(
                            "Your payment of %s could not be processed " +
                                    "Please try again or contact support",
                            amount
                    )
            );

        }catch (Exception e){
            log.error("Error sending payment failed notification: {}", e.getMessage());
        }
            }


    private void sendAlert(String accountNumber, String subject , String message){
        //FOR NOW WE USE LOGS, LATER WE WILL IMPLEMENT SMS/EMAIL

        log.info("----------------------------------------------");
        log.info("Account: {}", accountNumber);
        log.info("Subject: {}", subject);
        log.info("Message: {}", message);
        log.info("----------------------------------------------");




    }

}
