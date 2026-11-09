package com.emofid.balancechallenge.balance;

record StoredOperation(
		String transactionId,
		OperationType operationType,
		String sourceAccountId,
		String destinationAccountId,
		long amount,
		OperationStatus status,
		FailureCode failureCode,
		Long sourceBalance,
		Long destinationBalance
) {
	OperationResult toResult() {
		return new OperationResult(
				transactionId,
				operationType,
				status,
				sourceBalance,
				destinationBalance,
				failureCode
		);
	}
}
