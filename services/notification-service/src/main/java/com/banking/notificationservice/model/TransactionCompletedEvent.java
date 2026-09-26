package com.banking.notificationservice.model;

import java.math.BigDecimal;

public record TransactionCompletedEvent(
        String senderAccountNumber,
        String receiverAccountNumber,
        BigDecimal amount
) {
}