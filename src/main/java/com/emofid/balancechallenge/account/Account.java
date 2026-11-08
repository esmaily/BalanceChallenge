package com.emofid.balancechallenge.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "accounts")
public class Account {

	@Id
	@Column(length = 100, nullable = false, updatable = false)
	private String id;

	@Column(nullable = false)
	private long balance;

	@Version
	@Column(nullable = false)
	private long version;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Account() {
	}

	private Account(String id) {
		this.id = id;
	}

	public static Account open(String id) {
		return new Account(id);
	}

	public boolean credit(long amount) {
		try {
			balance = Math.addExact(balance, amount);
			return true;
		} catch (ArithmeticException ignored) {
			return false;
		}
	}

	public boolean debit(long amount) {
		if (balance < amount) {
			return false;
		}
		balance -= amount;
		return true;
	}

	public String getId() {
		return id;
	}

	public long getBalance() {
		return balance;
	}

	public long getVersion() {
		return version;
	}

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		createdAt = now;
		updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}
}
