package com.banking.notificationservice.model;

public record FraudDetectedEvent(
        String accountNumber,
        String reason
) {
}