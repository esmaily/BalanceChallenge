package com.emofid.balancechallenge.balance.exception;

public class TransactionConflictException extends RuntimeException {

	public TransactionConflictException(String transactionId) {
		super("Transaction ID is already used with a different request: " + transactionId);
	}
}
