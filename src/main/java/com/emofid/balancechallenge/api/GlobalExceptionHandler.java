package com.emofid.balancechallenge.api;

import com.emofid.balancechallenge.api.dto.ApiError;
import com.emofid.balancechallenge.balance.exception.AccountAlreadyExistsException;
import com.emofid.balancechallenge.balance.exception.AccountNotFoundException;
import com.emofid.balancechallenge.balance.exception.InvalidBalanceRequestException;
import com.emofid.balancechallenge.balance.exception.OperationInProgressException;
import com.emofid.balancechallenge.balance.exception.TransactionConflictException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(InvalidBalanceRequestException.class)
	ResponseEntity<ApiError> handleInvalidRequest(
			InvalidBalanceRequestException exception,
			HttpServletRequest request
	) {
		return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", exception.getMessage(), request);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ApiError> handleValidation(
			MethodArgumentNotValidException exception,
			HttpServletRequest request
	) {
		String message = exception.getBindingResult().getFieldErrors().stream()
				.findFirst()
				.map(error -> error.getField() + " " + error.getDefaultMessage())
				.orElse("Request validation failed");
		return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message, request);
	}

	@ExceptionHandler(AccountNotFoundException.class)
	ResponseEntity<ApiError> handleNotFound(AccountNotFoundException exception, HttpServletRequest request) {
		return error(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", exception.getMessage(), request);
	}

	@ExceptionHandler({
			AccountAlreadyExistsException.class,
			TransactionConflictException.class,
			OperationInProgressException.class,
			DataIntegrityViolationException.class
	})
	ResponseEntity<ApiError> handleConflict(Exception exception, HttpServletRequest request) {
		return error(HttpStatus.CONFLICT, "CONFLICT", exception.getMessage(), request);
	}

	private ResponseEntity<ApiError> error(
			HttpStatus status,
			String code,
			String message,
			HttpServletRequest request
	) {
		log.info(
				"API request rejected method={} path={} status={} code={}",
				request.getMethod(),
				request.getRequestURI(),
				status.value(),
				code
		);
		return ResponseEntity.status(status).body(new ApiError(
				Instant.now(),
				status.value(),
				code,
				message,
				request.getRequestURI()
		));
	}
}
