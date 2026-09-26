package com.banking.notificationservice.model;

import java.math.BigDecimal;

public record OtpGeneratedEvent(
        String accountNumber,
        String transactionId,
        String reason,
        String otp,
        BigDecimal amount
) {
}