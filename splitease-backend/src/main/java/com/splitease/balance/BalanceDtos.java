package com.splitease.balance;

import java.math.BigDecimal;
import java.util.UUID;

public class BalanceDtos {

    public record MemberBalance(
            UUID userId, String userName, BigDecimal totalPaid, BigDecimal totalOwed, BigDecimal netBalance
    ) {}
}
