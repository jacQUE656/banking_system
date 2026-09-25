package com.banking.transactionservice.enums;

/**
 * Transaction circle
 *  PENDING -> PROCESSING -> COMPLETED (completed transaction)
 *                        -> PENDING_VERIFICATION (suspicious activity detected)
 *                                               -> COMPLETED (verified)
 *                                               -> FLAGGED (SAGA Refund)
 *                        -> FAILED
 *                        -> FLAGGED
 *
 */
public enum TransactionStatus {
    PENDING,
    PROCESSING,
    PENDING_VERIFICATION,
    COMPLETED,
    FAILED,
    FLAGGED

}
