package com.splitease.expense;

import com.splitease.security.CurrentUserProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/groups/{groupId}/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public List<ExpenseDtos.ExpenseResponse> list(@PathVariable UUID groupId) {
        return expenseService.listExpenses(groupId, currentUserProvider.getCurrentUserId())
                .stream().map(this::toResponse).toList();
    }

    @GetMapping("/{expenseId}")
    public ExpenseDtos.ExpenseResponse get(@PathVariable UUID groupId, @PathVariable UUID expenseId) {
        return toResponse(expenseService.getExpense(groupId, expenseId, currentUserProvider.getCurrentUserId()));
    }

    @PostMapping
    public ResponseEntity<ExpenseDtos.ExpenseResponse> create(
            @PathVariable UUID groupId, @Valid @RequestBody ExpenseDtos.CreateExpenseRequest request) {
        var expense = expenseService.createExpense(groupId, currentUserProvider.getCurrentUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(expense));
    }

    @PutMapping("/{expenseId}")
    public ExpenseDtos.ExpenseResponse update(
            @PathVariable UUID groupId, @PathVariable UUID expenseId,
            @Valid @RequestBody ExpenseDtos.CreateExpenseRequest request) {
        var expense = expenseService.updateExpense(groupId, expenseId, currentUserProvider.getCurrentUserId(), request);
        return toResponse(expense);
    }

    @DeleteMapping("/{expenseId}")
    public ResponseEntity<Void> delete(@PathVariable UUID groupId, @PathVariable UUID expenseId) {
        expenseService.deleteExpense(groupId, expenseId, currentUserProvider.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    private ExpenseDtos.ExpenseResponse toResponse(Expense expense) {
        List<ExpenseDtos.SplitShareResponse> shares = expense.getSplits().stream()
                .map(s -> new ExpenseDtos.SplitShareResponse(s.getUser().getId(), s.getUser().getName(), s.getShareAmount()))
                .toList();
        return new ExpenseDtos.ExpenseResponse(
                expense.getId(), expense.getDescription(), expense.getAmount(),
                expense.getPayer().getId(), expense.getPayer().getName(),
                expense.getSplitType(), shares);
    }
}
