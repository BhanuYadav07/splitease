package com.splitease.expense;

import com.splitease.common.BaseEntity;
import com.splitease.group.Group;
import com.splitease.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "expense")
@Getter
@Setter
@NoArgsConstructor
public class Expense extends BaseEntity {

    public enum SplitType { EQUAL, UNEQUAL, PERCENTAGE }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payer_id", nullable = false)
    private User payer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SplitType splitType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @OneToMany(mappedBy = "expense", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ExpenseSplit> splits = new ArrayList<>();

    public Expense(Group group, String description, BigDecimal amount, User payer, SplitType splitType, User createdBy) {
        this.group = group;
        this.description = description;
        this.amount = amount;
        this.payer = payer;
        this.splitType = splitType;
        this.createdBy = createdBy;
    }

    public void addSplit(ExpenseSplit split) {
        splits.add(split);
        split.setExpense(this);
    }
}
