package com.emofid.balancechallenge.account;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.OptionalLong;

@Repository
public class AccountBalanceStore {

	private static final String CREDIT_SQL = """
			UPDATE accounts
			SET balance = balance + ?,
			    version = version + 1,
			    updated_at = CURRENT_TIMESTAMP
			WHERE id = ?
			  AND balance <= ?
			RETURNING balance
			""";

	private static final String DEBIT_SQL = """
			UPDATE accounts
			SET balance = balance - ?,
			    version = version + 1,
			    updated_at = CURRENT_TIMESTAMP
			WHERE id = ?
			  AND balance >= ?
			RETURNING balance
			""";

	private static final String FIND_BALANCE_SQL = """
			SELECT balance
			FROM accounts
			WHERE id = ?
			""";

	private final JdbcTemplate jdbcTemplate;

	public AccountBalanceStore(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public OptionalLong credit(String accountId, long amount) {
		return returningBalance(CREDIT_SQL, amount, accountId, Long.MAX_VALUE - amount);
	}

	public OptionalLong debit(String accountId, long amount) {
		return returningBalance(DEBIT_SQL, amount, accountId, amount);
	}

	public OptionalLong findBalance(String accountId) {
		return returningBalance(FIND_BALANCE_SQL, accountId);
	}

	private OptionalLong returningBalance(String sql, Object... arguments) {
		List<Long> balances = jdbcTemplate.query(
				sql,
				(resultSet, rowNumber) -> resultSet.getLong("balance"),
				arguments
		);
		return balances.isEmpty() ? OptionalLong.empty() : OptionalLong.of(balances.getFirst());
	}
}
