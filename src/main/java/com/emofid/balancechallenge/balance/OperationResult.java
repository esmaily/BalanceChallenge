package com.emofid.balancechallenge.balance;

public record OperationResult(
		String transactionId,
		OperationType operationType,
		OperationStatus status,
		Long sourceBalance,
		Long destinationBalance,
		FailureCode failureCode
) {
}
