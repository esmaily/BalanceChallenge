package com.emofid.balancechallenge.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateAccountRequest(
		@NotBlank
		@Size(max = 100)
		@Schema(example = "A")
		String accountId
) {
}
