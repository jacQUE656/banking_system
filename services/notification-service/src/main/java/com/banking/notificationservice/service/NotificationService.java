package com.banking.notificationservice.service;

import com.banking.notificationservice.client.AccountFeignClient;
import com.banking.notificationservice.mailing.EmailService;
import com.banking.notificationservice.model.FraudDetectedEvent;
import com.banking.notificationservice.model.OtpGeneratedEvent;
import com.banking.notificationservice.model.PaymentCompletedEvent;
import com.banking.notificationservice.model.PaymentFailedEvent;
import com.banking.notificationservice.model.TransactionCompletedEvent;
import com.banking.notificationservice.model.TransactionRefundedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationService {

    private final AccountFeignClient accountFeignClient;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;


    // OTP EVENT
    @KafkaListener(topics = "transaction.otp.generated")
    public void consumeOtpGenerated(@Payload Map<String, Object> payload) {
        try {
            OtpGeneratedEvent event = objectMapper.convertValue(payload, OtpGeneratedEvent.class);

            String message = String.format(
                    "Suspicious activity detected on your account. " +
                            "Reason: %s. " +
                            "A transaction of %s is pending verification. " +
                            "Your OTP is: %s. Valid for 5 minutes. " +
                            "If this wasn't you, ignore this message.",
                    event.reason(), event.amount(), event.otp()
            );

            sendAlert(event.accountNumber(), "TRANSACTION VERIFICATION REQUIRED", message);
        } catch (Exception e) {
            log.error("Error sending OTP notification", e);
        }
    }

    // TRANSACTION COMPLETED EVENT
    @KafkaListener(topics = "transaction.completed")
    public void consumeTransactionCompleted(@Payload Map<String, Object> payload) {
        try {
            TransactionCompletedEvent event =
                    objectMapper.convertValue(payload, TransactionCompletedEvent.class);

            sendAlert(event.senderAccountNumber(), "DEBIT ALERT",
                    String.format("%s debited from account %s",
                            event.amount(), event.senderAccountNumber()));

            sendAlert(event.receiverAccountNumber(), "CREDIT ALERT",
                    String.format("%s credited to account %s",
                            event.amount(), event.receiverAccountNumber()));
        } catch (Exception e) {
            log.error("Error sending transaction notification", e);
        }
    }

    //FRAUD EVENT
    @KafkaListener(topics = "fraud.detected")
    public void consumeFraudDetected(@Payload Map<String, Object> payload) {
        try {
            FraudDetectedEvent event = objectMapper.convertValue(payload, FraudDetectedEvent.class);

            String message = String.format(
                    "Your account %s has been blocked. Reason: %s. " +
                            "Please contact your bank immediately.",
                    event.accountNumber(), event.reason()
            );

            sendAlert(event.accountNumber(), "SUSPICIOUS ACTIVITY DETECTED", message);
        } catch (Exception e) {
            log.error("Error sending fraud alert", e);
        }
    }

    @KafkaListener(topics = "transaction.refunded")
    public void consumeTransactionRefunded(@Payload Map<String, Object> payload) {
        try {
            TransactionRefundedEvent event =
                    objectMapper.convertValue(payload, TransactionRefundedEvent.class);

            String message = String.format(
                    "Your transaction of %s has been refunded. Reason: %s. " +
                            "%s has been refunded to your account %s.",
                    event.amount(), event.reason(), event.amount(), event.senderAccountNumber()
            );

            sendAlert(event.senderAccountNumber(), "REFUND PROCESSED", message);
        } catch (Exception e) {
            log.error("Error sending refund transaction notification", e);
        }
    }

    @KafkaListener(topics = "payment.completed")
    public void consumePaymentCompleted(@Payload Map<String, Object> payload) {
        try {
            PaymentCompletedEvent event =
                    objectMapper.convertValue(payload, PaymentCompletedEvent.class);

            String message = String.format(
                    "Payment of %s completed. Razorpay ID %s",
                    event.amount(), event.razorpayPaymentId()
            );

            sendAlert(event.accountNumber(), "PAYMENT SUCCESSFUL", message);
        } catch (Exception e) {
            log.error("Error sending payment completed notification", e);
        }
    }

    @KafkaListener(topics = "payment.failed")
    public void consumePaymentFailed(@Payload Map<String, Object> payload) {
        try {
            PaymentFailedEvent event = objectMapper.convertValue(payload, PaymentFailedEvent.class);

            String message = String.format(
                    "Your payment of %s could not be processed. " +
                            "Please try again or contact support.",
                    event.amount()
            );

            sendAlert(event.accountNumber(), "PAYMENT FAILED", message);
        } catch (Exception e) {
            log.error("Error sending payment failed notification", e);
        }
    }

    /**
     * Looks up the account holder's email via accounting-service (through Eureka),
     * logs the alert for audit/debugging, and sends the actual email.
     * A lookup or send failure is logged but does not propagate — a failed
     * notification should never cause the triggering Kafka message to be redelivered.
     */
    private void sendAlert(String accountNumber, String subject, String message) {
        log.info("----------------------------------------------");
        log.info("Account: {}", accountNumber);
        log.info("Subject: {}", subject);
        log.info("Message: {}", message);
        log.info("----------------------------------------------");

        try {
            String email = accountFeignClient.getAccountEmail(accountNumber);
            emailService.sendEmail(email, subject, message);
        } catch (Exception e) {
            log.error("Failed to look up email or send notification for account {}", accountNumber, e);
        }
    }
}