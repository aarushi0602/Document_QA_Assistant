package com.aarushi.qa.dto;
import jakarta.validation.constraints.NotBlank;
public record AskRequest(@NotBlank String tenantId,@NotBlank String question) {}