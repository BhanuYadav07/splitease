package com.splitease.expense;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public class ExpenseDtos {

    public record ShareInput(
            @NotNull(message = "Participant userId is required") UUID userId,
            /** For EQUAL: ignored (server recomputes). For UNEQUAL: exact amount. For PERCENTAGE: percent 0-100. */
            @NotNull(message = "Share value is required") BigDecimal amount
    ) {}

    public record CreateExpenseRequest(
            @NotBlank(message = "Description is required") String description,
            @NotNull(message = "Amount is required")
            @DecimalMin(value = "0.01", message = "Amount must be greater than zero") BigDecimal amount,
            @NotNull(message = "Payer is required") UUID payerId,
            @NotNull(message = "Split type is required") Expense.SplitType splitType,
            @NotEmpty(message = "At least one participant is required")
            @Valid List<ShareInput> shares
    ) {}

    public record SplitShareResponse(UUID userId, String userName, BigDecimal amount) {}

    public record ExpenseResponse(
            UUID id, String description, BigDecimal amount, UUID payerId, String payerName,
            Expense.SplitType splitType, List<SplitShareResponse> shares
    ) {}
}
