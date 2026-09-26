package com.banking.notificationservice.model;

import java.math.BigDecimal;

public record TransactionRefundedEvent(
        String senderAccountNumber,
        BigDecimal amount,
        String reason
) {
}