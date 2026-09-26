package com.banking.notificationservice.model;

import java.math.BigDecimal;

public record PaymentFailedEvent(
        String accountNumber,
        BigDecimal amount
) {
}