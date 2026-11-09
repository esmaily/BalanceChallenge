package com.emofid.balancechallenge.api.dto;

import com.emofid.balancechallenge.balance.FailureCode;
import com.emofid.balancechallenge.balance.OperationResult;
import com.emofid.balancechallenge.balance.OperationStatus;
import com.emofid.balancechallenge.balance.OperationType;

public record OperationResponse(
		String transactionId,
		OperationType operationType,
		OperationStatus status,
		Long sourceBalance,
		Long destinationBalance,
		FailureCode failureCode
) {
	public static OperationResponse from(OperationResult result) {
		return new OperationResponse(
				result.transactionId(),
				result.operationType(),
				result.status(),
				result.sourceBalance(),
				result.destinationBalance(),
				result.failureCode()
		);
	}
}
