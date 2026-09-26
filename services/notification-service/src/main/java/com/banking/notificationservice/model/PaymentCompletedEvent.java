package com.banking.notificationservice.model;

import java.math.BigDecimal;

public record PaymentCompletedEvent(
        String accountNumber,
        BigDecimal amount,
        String razorpayPaymentId
) {
}