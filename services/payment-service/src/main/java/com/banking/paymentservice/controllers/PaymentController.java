package com.banking.paymentservice.controllers;

import com.banking.paymentservice.dto.PaymentRequest;
import com.banking.paymentservice.dto.PaymentResponse;
import com.banking.paymentservice.services.PaymentService;
import com.razorpay.RazorpayException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {
    private final PaymentService paymentService;

    @PostMapping("/create-order")
    public ResponseEntity<PaymentResponse> createPayment(
            @Valid @RequestBody PaymentRequest paymentRequest) throws RazorpayException {

        return ResponseEntity.status(HttpStatus.CREATED).body(
                paymentService.createPaymentOrder(paymentRequest)
        );
    }

    //  WEBHOOK CONTAINS ALL THE PAYMENT EVENTS, (SUCCESS, FAIL, PENDING, REFUND, CANCEL ....etc) WEB HOOK IS NOT COMPULSORY BUT RECOMMENDED
    private ResponseEntity<String> handleWebhook(@RequestBody Map<String, Object> payload ) {
            paymentService.handleWebhook(payload);
            return ResponseEntity.ok("Webhook processed");
    }




}
