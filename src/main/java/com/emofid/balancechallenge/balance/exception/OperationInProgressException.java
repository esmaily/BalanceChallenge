package com.emofid.balancechallenge.balance.exception;

public class OperationInProgressException extends RuntimeException {

	public OperationInProgressException(String transactionId) {
		super("Transaction is still in progress: " + transactionId);
	}
}
