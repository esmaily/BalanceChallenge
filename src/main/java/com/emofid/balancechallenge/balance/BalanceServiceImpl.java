package com.emofid.balancechallenge.balance;

import com.emofid.balancechallenge.account.Account;
import com.emofid.balancechallenge.account.AccountRepository;
import com.emofid.balancechallenge.balance.exception.AccountAlreadyExistsException;
import com.emofid.balancechallenge.balance.exception.AccountNotFoundException;
import com.emofid.balancechallenge.balance.exception.InvalidBalanceRequestException;
import com.emofid.balancechallenge.balance.exception.OperationInProgressException;
import com.emofid.balancechallenge.balance.exception.TransactionConflictException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.OptionalLong;
import java.util.concurrent.locks.StampedLock;

/**
 * Executes idempotent balance operations in a single database transaction.
 * Operation claims prevent duplicate effects, while row locks serialize balance changes.
 */
@Slf4j
@Service

class BalanceServiceImpl implements BalanceService {

	private static final int MAX_IDENTIFIER_LENGTH = 100;

	private final AccountRepository accountRepository;
	private final OperationStore operationStore;

	BalanceServiceImpl(AccountRepository accountRepository, OperationStore operationStore) {
		this.accountRepository = accountRepository;
		this.operationStore = operationStore;

	}


	@Transactional
	public void createAccount(String accountId) {
		validateIdentifier(accountId, "accountId");
		if (accountRepository.existsById(accountId)) {
			throw new AccountAlreadyExistsException(accountId);
		}
		accountRepository.saveAndFlush(Account.open(accountId));
	}


	@Transactional
	public OperationResult credit(String accountId, long amount, String transactionId) {
		validateOperation(accountId, amount, transactionId);
		OperationRequest request = new OperationRequest(
				transactionId, OperationType.CREDIT, null, accountId, amount
		);

		// Claim first so exact retries do not lock the account unnecessarily.
		OptionalLong claim = operationStore.tryClaim(request);
		if (claim.isEmpty()) {
			return replay(request);
		}

		// Serialize different transactions that update the same account.
		Account account = accountRepository.findByIdForUpdate(accountId).orElse(null);
 		if (account == null) {
			return reject(request, claim.getAsLong(), FailureCode.ACCOUNT_NOT_FOUND, null, null);
		}
		if (!account.credit(amount)) {
			return reject(
					request, claim.getAsLong(), FailureCode.BALANCE_OVERFLOW, null, account.getBalance()
			);
		}
		return succeed(request, claim.getAsLong(), null, account.getBalance());
	}


	@Transactional
	public OperationResult debit(String accountId, long amount, String transactionId) {
		validateOperation(accountId, amount, transactionId);
		OperationRequest request = new OperationRequest(
				transactionId, OperationType.DEBIT, accountId, null, amount
		);

		OptionalLong claim = operationStore.tryClaim(request);
		if (claim.isEmpty()) {
			return replay(request);
		}

		Account account = accountRepository.findByIdForUpdate(accountId).orElse(null);
		if (account == null) {
			return reject(request, claim.getAsLong(), FailureCode.ACCOUNT_NOT_FOUND, null, null);
		}
		if (!account.debit(amount)) {
			return reject(
					request, claim.getAsLong(), FailureCode.INSUFFICIENT_FUNDS, account.getBalance(), null
			);
		}
		return succeed(request, claim.getAsLong(), account.getBalance(), null);
	}


	@Transactional
	public OperationResult transfer(
			String sourceAccountId,
			String destinationAccountId,
			long amount,
			String transactionId
	) {
		validateOperation(sourceAccountId, amount, transactionId);
		validateIdentifier(destinationAccountId, "destinationAccountId");
		if (sourceAccountId.equals(destinationAccountId)) {
			throw new InvalidBalanceRequestException("Source and destination accounts must be different");
		}

		OperationRequest request = new OperationRequest(
				transactionId,
				OperationType.TRANSFER,
				sourceAccountId,
				destinationAccountId,
				amount
		);

		OptionalLong claim = operationStore.tryClaim(request);
		if (claim.isEmpty()) {
			return replay(request);
		}

		// A stable lock order prevents opposite transfers from creating a deadlock.
		String firstId = sourceAccountId.compareTo(destinationAccountId) < 0 ? sourceAccountId : destinationAccountId;
		String secondId = firstId.equals(sourceAccountId) ? destinationAccountId : sourceAccountId;

		Account first = accountRepository.findByIdForUpdate(firstId).orElse(null);
		if (first == null) {
			return reject(request, claim.getAsLong(), FailureCode.ACCOUNT_NOT_FOUND, null, null);
		}
		Account second = accountRepository.findByIdForUpdate(secondId).orElse(null);
		if (second == null) {
			return reject(request, claim.getAsLong(), FailureCode.ACCOUNT_NOT_FOUND, null, null);
		}

		Account source = sourceAccountId.equals(firstId) ? first : second;
		Account destination = destinationAccountId.equals(firstId) ? first : second;
		if (source.getBalance() < amount) {
			return reject(
					request,
					claim.getAsLong(),
					FailureCode.INSUFFICIENT_FUNDS,
					source.getBalance(),
					destination.getBalance()
			);
		}
		if (!destination.credit(amount)) {
			return reject(
					request,
					claim.getAsLong(),
					FailureCode.BALANCE_OVERFLOW,
					source.getBalance(),
					destination.getBalance()
			);
		}
		source.debit(amount);
		return succeed(
				request,
				claim.getAsLong(),
				source.getBalance(),
				destination.getBalance()
		);
	}


	@Transactional(readOnly = true)
	public long getBalance(String accountId) {
		validateIdentifier(accountId, "accountId");
		return accountRepository.findById(accountId)
				.map(Account::getBalance)
				.orElseThrow(() -> new AccountNotFoundException(accountId));
	}

	private OperationResult replay(OperationRequest request) {
		StoredOperation stored = operationStore.findRequired(request.transactionId());
		// A transaction ID can be retried only with its original payload.
		if (!request.matches(stored)) {
			log.warn(
					"Balance operation transaction conflict transactionId={} requestedType={} storedType={}",
					request.transactionId(),
					request.operationType(),
					stored.operationType()
			);
			throw new TransactionConflictException(request.transactionId());
		}
		if (stored.status() == OperationStatus.PENDING) {
			log.info(
					"Balance operation is still pending transactionId={} operationType={}",
					request.transactionId(),
					request.operationType()
			);
			throw new OperationInProgressException(request.transactionId());
		}
		log.debug(
				"Balance operation replayed transactionId={} operationType={} status={}",
				request.transactionId(),
				stored.operationType(),
				stored.status()
		);
		return stored.toResult();
	}

	private OperationResult succeed(
			OperationRequest request,
			long fencingToken,
			Long sourceBalance,
			Long destinationBalance
	) {
		operationStore.complete(
				request.transactionId(),
				fencingToken,
				OperationStatus.SUCCEEDED,
				null,
				sourceBalance,
				destinationBalance
		);
		return new OperationResult(
				request.transactionId(),
				request.operationType(),
				OperationStatus.SUCCEEDED,
				sourceBalance,
				destinationBalance,
				null
		);
	}

	private OperationResult reject(
			OperationRequest request,
			long fencingToken,
			FailureCode failureCode,
			Long sourceBalance,
			Long destinationBalance
	) {
		operationStore.complete(
				request.transactionId(),
				fencingToken,
				OperationStatus.REJECTED,
				failureCode,
				sourceBalance,
				destinationBalance
		);
		return new OperationResult(
				request.transactionId(),
				request.operationType(),
				OperationStatus.REJECTED,
				sourceBalance,
				destinationBalance,
				failureCode
		);
	}

	private void validateOperation(String accountId, long amount, String transactionId) {
		validateIdentifier(accountId, "accountId");
		validateIdentifier(transactionId, "transactionId");
		if (amount <= 0) {
			throw new InvalidBalanceRequestException("Amount must be greater than zero");
		}
	}

	private void validateIdentifier(String value, String fieldName) {
		if (value == null || value.isBlank()) {
			throw new InvalidBalanceRequestException(fieldName + " must not be blank");
		}
		if (value.length() > MAX_IDENTIFIER_LENGTH) {
			throw new InvalidBalanceRequestException(fieldName + " must not exceed 100 characters");
		}
		if (!value.equals(value.trim())) {
			throw new InvalidBalanceRequestException(fieldName + " must not contain surrounding spaces");
		}
	}
}
