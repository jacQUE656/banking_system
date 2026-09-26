package com.banking.transactionservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OtpVerificationRequest {

    @NotBlank
    private String otp;

}