package com.emofid.balancechallenge.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record DebitRequest(
		@NotBlank @Size(max = 100) @Schema(example = "A") String accountId,
		@Positive @Schema(example = "300") long amount,
		@NotBlank @Size(max = 100) @Schema(example = "TX-101") String transactionId
) {
}
