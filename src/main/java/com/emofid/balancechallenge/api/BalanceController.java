package com.emofid.balancechallenge.api;

import com.emofid.balancechallenge.api.dto.CreditRequest;
import com.emofid.balancechallenge.api.dto.DebitRequest;
import com.emofid.balancechallenge.api.dto.OperationResponse;
import com.emofid.balancechallenge.api.dto.TransferRequest;
import com.emofid.balancechallenge.balance.BalanceService;
import com.emofid.balancechallenge.balance.FailureCode;
import com.emofid.balancechallenge.balance.OperationResult;
import com.emofid.balancechallenge.balance.OperationStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/transactions")
@Tag(name = "Balance transactions")
public class BalanceController {

	private final BalanceService balanceService;

	public BalanceController(BalanceService balanceService) {
		this.balanceService = balanceService;
	}

	@PostMapping("/credits")
	@Operation(summary = "Credit an account exactly once for a transaction ID")
	public ResponseEntity<OperationResponse> credit(@Valid @RequestBody CreditRequest request) {
		OperationResult result = balanceService.credit(
				request.accountId(), request.amount(), request.transactionId()
		);
		return toResponse(result);
	}

	@PostMapping("/debits")
	@Operation(summary = "Debit an account when sufficient balance is available")
	public ResponseEntity<OperationResponse> debit(@Valid @RequestBody DebitRequest request) {
		OperationResult result = balanceService.debit(
				request.accountId(), request.amount(), request.transactionId()
		);
		return toResponse(result);
	}

	@PostMapping("/transfers")
	@Operation(summary = "Atomically transfer balance between two accounts")
	public ResponseEntity<OperationResponse> transfer(@Valid @RequestBody TransferRequest request) {
		OperationResult result = balanceService.transfer(
				request.sourceAccountId(),
				request.destinationAccountId(),
				request.amount(),
				request.transactionId()
		);
		return toResponse(result);
	}

	private ResponseEntity<OperationResponse> toResponse(OperationResult result) {
		log.info(
				"Balance operation completed transactionId={} operationType={} status={} failureCode={}",
				result.transactionId(),
				result.operationType(),
				result.status(),
				result.failureCode()
		);
		if (result.status() == OperationStatus.SUCCEEDED) {
			return ResponseEntity.ok(OperationResponse.from(result));
		}
		HttpStatus status = result.failureCode() == FailureCode.ACCOUNT_NOT_FOUND
				? HttpStatus.NOT_FOUND : HttpStatus.UNPROCESSABLE_CONTENT;
		return ResponseEntity.status(status).body(OperationResponse.from(result));
	}
}
