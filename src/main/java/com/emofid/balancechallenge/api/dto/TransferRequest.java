package com.emofid.balancechallenge.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record TransferRequest(
		@NotBlank @Size(max = 100) @Schema(example = "A") String sourceAccountId,
		@NotBlank @Size(max = 100) @Schema(example = "B") String destinationAccountId,
		@Positive @Schema(example = "200") long amount,
		@NotBlank @Size(max = 100) @Schema(example = "TX-102") String transactionId
) {
}
