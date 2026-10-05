package com.splitease.settlement;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class SettlementDtos {

    public record RecordSettlementRequest(
            @NotNull(message = "fromUserId is required") UUID fromUserId,
            @NotNull(message = "toUserId is required") UUID toUserId,
            @NotNull(message = "Amount is required")
            @DecimalMin(value = "0.01", message = "Settlement amount must be greater than zero") BigDecimal amount
    ) {}

    public record SuggestedSettlementResponse(
            UUID fromUserId, String fromUserName, UUID toUserId, String toUserName, BigDecimal amount
    ) {}

    public record SettlementHistoryResponse(
            UUID id, UUID fromUserId, String fromUserName, UUID toUserId, String toUserName,
            BigDecimal amount, String status, Instant settledAt
    ) {}
}
