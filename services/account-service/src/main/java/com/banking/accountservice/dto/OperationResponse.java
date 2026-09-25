package com.banking.accountservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OperationResponse {

    private String status;
    private String message;

    public static OperationResponse success(String message) {
        return new OperationResponse("SUCCESS", message);
    }
}