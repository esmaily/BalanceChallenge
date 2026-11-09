package com.emofid.balancechallenge.balance;

public interface BalanceService {

	void createAccount(String accountId);

	OperationResult credit(String accountId, long amount, String transactionId);

	OperationResult debit(String accountId, long amount, String transactionId);

	OperationResult transfer(
			String sourceAccountId,
			String destinationAccountId,
			long amount,
			String transactionId
	);

	long getBalance(String accountId);
}
