package com.cotizaia.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Validated input for agency operations. */
public record AnswerQuestionRequest(@NotBlank @Size(max = 2000) String answer) {
}
