package com.emofid.balancechallenge.api;

import com.emofid.balancechallenge.api.dto.AccountBalanceResponse;
import com.emofid.balancechallenge.api.dto.CreateAccountRequest;
import com.emofid.balancechallenge.balance.BalanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@Slf4j
@RestController
@RequestMapping("/api/v1/accounts")
@Tag(name = "Accounts")
public class AccountController {

	private final BalanceService balanceService;

	public AccountController(BalanceService balanceService) {
		this.balanceService = balanceService;
	}

	@PostMapping
	@Operation(summary = "Create an account with zero balance")
	public ResponseEntity<AccountBalanceResponse> create(@Valid @RequestBody CreateAccountRequest request) {
		balanceService.createAccount(request.accountId());
		log.info("Account created accountId={}", request.accountId());
		AccountBalanceResponse response = new AccountBalanceResponse(request.accountId(), 0);
		return ResponseEntity
				.created(URI.create("/api/v1/accounts/" + request.accountId()))
				.body(response);
	}

	@GetMapping("/{accountId}/balance")
	@Operation(summary = "Get the current committed account balance")
	public AccountBalanceResponse getBalance(@PathVariable String accountId) {
		long balance = balanceService.getBalance(accountId);
		log.debug("Account balance retrieved accountId={}", accountId);
		return new AccountBalanceResponse(accountId, balance);
	}
}
