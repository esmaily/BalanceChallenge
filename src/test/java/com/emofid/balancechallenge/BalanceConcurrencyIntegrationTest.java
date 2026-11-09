package com.emofid.balancechallenge;

import com.emofid.balancechallenge.balance.BalanceService;
import com.emofid.balancechallenge.balance.FailureCode;
import com.emofid.balancechallenge.balance.OperationResult;
import com.emofid.balancechallenge.balance.OperationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class BalanceConcurrencyIntegrationTest extends PostgresIntegrationTest {

	private static final int WORKER_COUNT = 16;

	@Autowired
	private BalanceService balanceService;

	@Test
	void concurrentDebitsCannotOverspendAnAccount() throws Exception {
		balanceService.createAccount("A");
		balanceService.credit("A", 1_000, "seed-A");

		List<OperationResult> results = runConcurrently(List.of(
				() -> balanceService.debit("A", 700, "debit-1"),
				() -> balanceService.debit("A", 700, "debit-2")
		));

		assertThat(results).filteredOn(result -> result.status() == OperationStatus.SUCCEEDED).hasSize(1);
		assertThat(results).filteredOn(result -> result.failureCode() == FailureCode.INSUFFICIENT_FUNDS)
				.hasSize(1);
		assertThat(balanceService.getBalance("A")).isEqualTo(300);
	}

	@Test
	void concurrentDuplicateCreditsHaveExactlyOneEffect() throws Exception {
		balanceService.createAccount("A");
		List<Callable<OperationResult>> calls = new ArrayList<>();
		for (int index = 0; index < 32; index++) {
			calls.add(() -> balanceService.credit("A", 100, "one-logical-credit"));
		}

		List<OperationResult> results = runConcurrently(calls);

		assertThat(results).allMatch(result -> result.status() == OperationStatus.SUCCEEDED);
		assertThat(results).extracting(OperationResult::destinationBalance).containsOnly(100L);
		assertThat(balanceService.getBalance("A")).isEqualTo(100);
		assertThat(operationCount("one-logical-credit")).isEqualTo(1);
	}

	@Test
	void concurrentUniqueCreditsDoNotLoseUpdates() throws Exception {
		balanceService.createAccount("A");
		List<Callable<OperationResult>> calls = new ArrayList<>();
		for (int index = 0; index < 80; index++) {
			String transactionId = "credit-" + index;
			calls.add(() -> balanceService.credit("A", 10, transactionId));
		}

		List<OperationResult> results = runConcurrently(calls);

		assertThat(results).allMatch(result -> result.status() == OperationStatus.SUCCEEDED);
		assertThat(balanceService.getBalance("A")).isEqualTo(800);
	}

	@Test
	void oppositeTransfersUseStableLockOrderAndPreserveTotalBalance() throws Exception {
		balanceService.createAccount("A");
		balanceService.createAccount("B");
		balanceService.credit("A", 10_000, "seed-A");
		balanceService.credit("B", 10_000, "seed-B");

		List<Callable<OperationResult>> calls = new ArrayList<>();
		for (int index = 0; index < 40; index++) {
			String aToB = "A-B-" + index;
			String bToA = "B-A-" + index;
			calls.add(() -> balanceService.transfer("A", "B", 25, aToB));
			calls.add(() -> balanceService.transfer("B", "A", 25, bToA));
		}

		List<OperationResult> results = runConcurrently(calls);

		assertThat(results).allMatch(result -> result.status() == OperationStatus.SUCCEEDED);
		long firstBalance = balanceService.getBalance("A");
		long secondBalance = balanceService.getBalance("B");
		assertThat(firstBalance).isEqualTo(10_000);
		assertThat(secondBalance).isEqualTo(10_000);
		assertThat(firstBalance + secondBalance).isEqualTo(20_000);
	}

	private <T> List<T> runConcurrently(List<Callable<T>> calls) throws Exception {
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(Math.min(WORKER_COUNT, calls.size()));
		try {
			List<Future<T>> futures = calls.stream()
					.map(call -> executor.submit(() -> {
						start.await();
						return call.call();
					}))
					.toList();
			start.countDown();

			List<T> results = new ArrayList<>(futures.size());
			for (Future<T> future : futures) {
				results.add(future.get(30, TimeUnit.SECONDS));
			}
			return results;
		} finally {
			executor.shutdownNow();
			executor.awaitTermination(5, TimeUnit.SECONDS);
		}
	}
}
