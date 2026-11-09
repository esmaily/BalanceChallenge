package com.emofid.balancechallenge.balance;

import java.util.Objects;

record OperationRequest(
		String transactionId,
		OperationType operationType,
		String sourceAccountId,
		String destinationAccountId,
		long amount
) {
	boolean matches(StoredOperation stored) {
		return operationType == stored.operationType()
				&& Objects.equals(sourceAccountId, stored.sourceAccountId())
				&& Objects.equals(destinationAccountId, stored.destinationAccountId())
				&& amount == stored.amount();
	}
}
