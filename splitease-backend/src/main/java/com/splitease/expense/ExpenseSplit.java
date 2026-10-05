package com.splitease.expense;

import com.splitease.common.BaseEntity;
import com.splitease.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "expense_split", uniqueConstraints = @UniqueConstraint(columnNames = {"expense_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
public class ExpenseSplit extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "expense_id", nullable = false)
    private Expense expense;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** The participant's monetary share of the expense, in the group's currency minor-unit-safe decimal. */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal shareAmount;

    public ExpenseSplit(User user, BigDecimal shareAmount) {
        this.user = user;
        this.shareAmount = shareAmount;
    }
}
