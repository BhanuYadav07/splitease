package com.splitease.expense;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExpenseSplitRepository extends JpaRepository<ExpenseSplit, UUID> {
    List<ExpenseSplit> findByExpenseGroupId(UUID groupId);
    List<ExpenseSplit> findByExpenseId(UUID expenseId);
}
