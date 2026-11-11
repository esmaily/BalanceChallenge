package com.emofid.balancechallenge.balance;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;

@Repository
class OperationStore {

	private static final String CLAIM_SQL = """
			INSERT INTO balance_operation (
			    transaction_id, operation_type, source_account_id,
			    destination_account_id, amount, status
			)
			VALUES (?, ?, ?, ?, ?, 'PENDING')
			ON CONFLICT (transaction_id) DO NOTHING
			""";

	private static final String FIND_SQL = """
			SELECT transaction_id, operation_type, source_account_id,
			       destination_account_id, amount, status, failure_code,
			       source_balance_after, destination_balance_after
			FROM balance_operation
			WHERE transaction_id = ?
			""";

	private static final String COMPLETE_SQL = """
			UPDATE balance_operation
			SET status = ?, failure_code = ?, source_balance_after = ?,
			    destination_balance_after = ?, completed_at = CURRENT_TIMESTAMP
			WHERE transaction_id = ?
			  AND status = 'PENDING'
			""";

	private final JdbcTemplate jdbcTemplate;

	OperationStore(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	boolean tryClaim(OperationRequest request) {
		int inserted = jdbcTemplate.update(
				CLAIM_SQL,
				request.transactionId(),
				request.operationType().name(),
				request.sourceAccountId(),
				request.destinationAccountId(),
				request.amount()
		);
		return inserted == 1;
	}

	StoredOperation findRequired(String transactionId) {
		return jdbcTemplate.query(FIND_SQL, this::readOperation, transactionId)
				.stream()
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("Claimed operation was not found"));
	}

	void complete(
			String transactionId,
			OperationStatus status,
			FailureCode failureCode,
			Long sourceBalance,
			Long destinationBalance
	) {
		int updated = jdbcTemplate.update(
				COMPLETE_SQL,
				status.name(),
				failureCode == null ? null : failureCode.name(),
				sourceBalance,
				destinationBalance,
				transactionId
		);
		if (updated != 1) {
			throw new IllegalStateException("Claimed operation is no longer pending");
		}
	}

	private StoredOperation readOperation(ResultSet resultSet, int rowNumber) throws SQLException {
		String failureCode = resultSet.getString("failure_code");
		return new StoredOperation(
				resultSet.getString("transaction_id"),
				OperationType.valueOf(resultSet.getString("operation_type")),
				resultSet.getString("source_account_id"),
				resultSet.getString("destination_account_id"),
				resultSet.getLong("amount"),
				OperationStatus.valueOf(resultSet.getString("status")),
				failureCode == null ? null : FailureCode.valueOf(failureCode),
				resultSet.getObject("source_balance_after", Long.class),
				resultSet.getObject("destination_balance_after", Long.class)
		);
	}
}
