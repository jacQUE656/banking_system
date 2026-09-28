package com.banking.paymentservice.services;

import com.banking.paymentservice.dto.PaymentRequest;
import com.banking.paymentservice.dto.PaymentResponse;
import com.banking.paymentservice.entity.Payment;
import com.banking.paymentservice.enums.PaymentStatus;
import com.banking.paymentservice.repository.PaymentRepository;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${razorpay.api.key}")
    private String keyId;

    @Value("${razorpay.api.secret}")
    private String keySecret;

    private static final String PAYMENT_COMPLETED_TOPIC = "payment.completed";
    private static final String PAYMENT_FAILED_TOPIC = "payment.failed";

    /**
     * Create Razorpay payment order
     * FLOW
     * 1. Create order in Razorpay
     * 2. Save payment record in DB
     * 3. Return order details to frontend
     * 4. Frontend shows Razorpay checkout
     * 5. User pays
     * 6. Razorpay calls webhook
     * @param paymentRequest
     * @return
     */
    public PaymentResponse createPaymentOrder(PaymentRequest paymentRequest) throws RazorpayException {
        log.info("Creating payment order for account: {} amount: {}",
                paymentRequest.getAccountNumber(), paymentRequest.getAmount());

        RazorpayClient razorpayClient = new RazorpayClient(keyId, keySecret);

        // CONVERT THE AMOUNT TO CENTS / PAISE (Multiply by 100)
        int convertedAmount = paymentRequest.getAmount()
                .multiply(BigDecimal.valueOf(100))
                .intValue();

        // Create order in Razorpay
        JSONObject orderRequest = new JSONObject();
        orderRequest.put("amount", convertedAmount);
        orderRequest.put("currency", "USD");
        orderRequest.put("receipt", "rcpt" + System.currentTimeMillis() + UUID.randomUUID().toString().replace("-", "").substring(0, 10));

        Order razorpayOrder = razorpayClient.orders.create(orderRequest);
        String orderId = razorpayOrder.get("id").toString();

        // SAVE PAYMENT
        Payment payment = Payment.builder()
                .razorpayOrderId(orderId)
                .accountNumber(paymentRequest.getAccountNumber())
                .amount(paymentRequest.getAmount())
                .currency("USD")
                .paymentStatus(PaymentStatus.CREATED)
                .description(paymentRequest.getDescription())
                .build();
        Payment savedPayment = paymentRepository.save(payment);

        log.info("Razorpay Order Created: {}", orderId);
        return PaymentResponse.builder()
                .paymentId(savedPayment.getId())
                .razorpayOrderId(orderId)
                .amount(savedPayment.getAmount())
                .currency(savedPayment.getCurrency())
                .status("CREATED")
                .razorpayKeyId(keyId)
                .build();
    }

    public void handleWebhook(Map<String, Object> payload) {
        log.info("Received RazorPay Webhook : {}", payload.get("event"));
        String event = (String) payload.get("event");
        if ("payment.captured".equals(event)) {
            handlePaymentSuccess(payload);
        } else if ("payment.failed".equals(event)) {
            handlePaymentFailure(payload);
        }
    }

    private void handlePaymentFailure(Map<String, Object> payload) {
        try {
            Map<String, Object> paymentData = extractPaymentData(payload);
            String orderId = (String) paymentData.get("order_id");

            Payment payment = paymentRepository.findByRazorpayOrderId(orderId)
                    .orElseThrow(() -> new RuntimeException("Payment not found for order Id: " + orderId));

            payment.setPaymentStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Payment failed via Razorpay API");
            paymentRepository.save(payment);

            // PUBLISH PAYMENT FAILED EVENT
            Map<String, Object> event = new HashMap<>();
            event.put("paymentId", payment.getId());
            event.put("accountNumber", payment.getAccountNumber());
            event.put("amount", payment.getAmount());
            event.put("reason", "Payment failed via Razorpay API");

            kafkaTemplate.send(PAYMENT_FAILED_TOPIC, payment.getId(), event);
            log.warn("Payment failed : {}", payment.getId());
        } catch (Exception e) {
            log.error("Error occurred while processing failed payment :{}", e.getMessage());
        }
    }

    private void handlePaymentSuccess(Map<String, Object> payload) {
        try {
            Map<String, Object> paymentData = extractPaymentData(payload);
            String orderId = (String) paymentData.get("order_id");
            String paymentId = (String) paymentData.get("id");

            Payment payment = paymentRepository.findByRazorpayOrderId(orderId)
                    .orElseThrow(() -> new RuntimeException("Payment not found for order Id: " + orderId));

            // Keep original razorpayOrderId and set the razorpayPaymentId separately
            payment.setRazorpayPaymentId(paymentId);
            payment.setPaymentStatus(PaymentStatus.COMPLETED);
            paymentRepository.save(payment);

            // PUBLISH PAYMENT COMPLETED EVENT
            Map<String, Object> event = new HashMap<>();
            event.put("paymentId", payment.getId());
            event.put("accountNumber", payment.getAccountNumber());
            event.put("amount", payment.getAmount());
            event.put("razorpayPaymentId", paymentId);

            kafkaTemplate.send(PAYMENT_COMPLETED_TOPIC, payment.getId(), event);
            log.info("Payment Completed Successfully : {}", payment.getId());
        } catch (Exception e) {
            log.error("Error handling payment : {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractPaymentData(Map<String, Object> payload) {
        Map<String, Object> entity = (Map<String, Object>) payload.get("payload");
        Map<String, Object> paymentWrapper = (Map<String, Object>) entity.get("payment");
        return (Map<String, Object>) paymentWrapper.get("entity");
    }
}