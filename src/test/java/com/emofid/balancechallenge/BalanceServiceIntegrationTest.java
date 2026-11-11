package com.emofid.balancechallenge;

import com.emofid.balancechallenge.balance.BalanceService;
import com.emofid.balancechallenge.balance.FailureCode;
import com.emofid.balancechallenge.balance.OperationResult;
import com.emofid.balancechallenge.balance.OperationStatus;
import com.emofid.balancechallenge.balance.exception.InvalidBalanceRequestException;
import com.emofid.balancechallenge.balance.exception.TransactionConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BalanceServiceIntegrationTest extends PostgresIntegrationTest {

	@Autowired
	private BalanceService balanceService;

	@Test
	void creditDebitAndTransferAreCommittedAtomically() {
		balanceService.createAccount("A");
		balanceService.createAccount("B");

		OperationResult credit = balanceService.credit("A", 1_000, "credit-A");
		OperationResult debit = balanceService.debit("A", 200, "debit-A");
		OperationResult transfer = balanceService.transfer("A", "B", 300, "transfer-A-B");

		assertThat(credit.status()).isEqualTo(OperationStatus.SUCCEEDED);
		assertThat(debit.status()).isEqualTo(OperationStatus.SUCCEEDED);
		assertThat(transfer.status()).isEqualTo(OperationStatus.SUCCEEDED);
		assertThat(balanceService.getBalance("A")).isEqualTo(500);
		assertThat(balanceService.getBalance("B")).isEqualTo(300);
	}

	@Test
	void insufficientDebitIsRejectedWithoutChangingBalance() {
		balanceService.createAccount("A");
		balanceService.credit("A", 100, "seed-A");

		OperationResult result = balanceService.debit("A", 101, "too-large-debit");

		assertThat(result.status()).isEqualTo(OperationStatus.REJECTED);
		assertThat(result.failureCode()).isEqualTo(FailureCode.INSUFFICIENT_FUNDS);
		assertThat(result.sourceBalance()).isEqualTo(100);
		assertThat(balanceService.getBalance("A")).isEqualTo(100);
	}

	@Test
	void creditOverflowIsRejectedWithoutChangingBalance() {
		balanceService.createAccount("A");
		balanceService.credit("A", Long.MAX_VALUE, "max-credit");

		OperationResult result = balanceService.credit("A", 1, "overflow-credit");

		assertThat(result.status()).isEqualTo(OperationStatus.REJECTED);
		assertThat(result.failureCode()).isEqualTo(FailureCode.BALANCE_OVERFLOW);
		assertThat(result.destinationBalance()).isEqualTo(Long.MAX_VALUE);
		assertThat(balanceService.getBalance("A")).isEqualTo(Long.MAX_VALUE);
	}

	@Test
	void exactRetryReturnsStoredResultWithoutApplyingCreditTwice() {
		balanceService.createAccount("A");

		OperationResult first = balanceService.credit("A", 250, "same-credit");
		OperationResult retry = balanceService.credit("A", 250, "same-credit");

		assertThat(retry).isEqualTo(first);
		assertThat(balanceService.getBalance("A")).isEqualTo(250);
		assertThat(operationCount("same-credit")).isEqualTo(1);
	}

	@Test
	void reusingTransactionIdWithDifferentPayloadIsAConflict() {
		balanceService.createAccount("A");
		balanceService.credit("A", 100, "reused-id");

		assertThatThrownBy(() -> balanceService.credit("A", 101, "reused-id"))
				.isInstanceOf(TransactionConflictException.class);
		assertThat(balanceService.getBalance("A")).isEqualTo(100);
	}

	@Test
	void transferToSameAccountIsRejectedBeforeClaimingTransaction() {
		balanceService.createAccount("A");

		assertThatThrownBy(() -> balanceService.transfer("A", "A", 1, "invalid-transfer"))
				.isInstanceOf(InvalidBalanceRequestException.class);
		assertThat(operationCount("invalid-transfer")).isZero();
	}

	@Test
	void missingAccountFailureIsRecordedAndReplayable() {
		OperationResult first = balanceService.credit("missing", 50, "missing-credit");
		OperationResult retry = balanceService.credit("missing", 50, "missing-credit");

		assertThat(first.status()).isEqualTo(OperationStatus.REJECTED);
		assertThat(first.failureCode()).isEqualTo(FailureCode.ACCOUNT_NOT_FOUND);
		assertThat(retry).isEqualTo(first);
		assertThat(operationCount("missing-credit")).isEqualTo(1);
	}
}
