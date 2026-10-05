package com.splitease.settlement;

import com.splitease.balance.BalanceDtos;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Cash-flow / debt-simplification algorithm (functional requirement 8).
 *
 * Given each member's net balance, greedily matches the largest creditor
 * against the largest debtor using two max-heaps, settling the smaller of
 * the two amounts each round. This minimizes the number of payment
 * transactions needed to settle a group ("A owes B, B owes C" collapses to
 * "A owes C" whenever the amounts allow it).
 */
public class CashFlowAlgorithm {

    private static final BigDecimal EPSILON = new BigDecimal("0.01");

    public record Node(java.util.UUID userId, String name, BigDecimal amount) {}

    public record Transaction(java.util.UUID fromUserId, String fromUserName,
                               java.util.UUID toUserId, String toUserName, BigDecimal amount) {}

    public List<Transaction> simplify(List<BalanceDtos.MemberBalance> balances) {
        // Max-heap of creditors (people owed money, net > 0) ordered by amount desc.
        PriorityQueue<Node> creditors = new PriorityQueue<>(Comparator.comparing(Node::amount).reversed());
        // Max-heap of debtors (people who owe money, net < 0, stored as positive amount) ordered by amount desc.
        PriorityQueue<Node> debtors = new PriorityQueue<>(Comparator.comparing(Node::amount).reversed());

        for (BalanceDtos.MemberBalance b : balances) {
            if (b.netBalance().compareTo(EPSILON) > 0) {
                creditors.add(new Node(b.userId(), b.userName(), b.netBalance()));
            } else if (b.netBalance().compareTo(EPSILON.negate()) < 0) {
                debtors.add(new Node(b.userId(), b.userName(), b.netBalance().abs()));
            }
        }

        List<Transaction> transactions = new ArrayList<>();

        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            Node creditor = creditors.poll();
            Node debtor = debtors.poll();

            BigDecimal settled = creditor.amount().min(debtor.amount());
            transactions.add(new Transaction(debtor.userId(), debtor.name(), creditor.userId(), creditor.name(), settled));

            BigDecimal creditorRemaining = creditor.amount().subtract(settled);
            BigDecimal debtorRemaining = debtor.amount().subtract(settled);

            if (creditorRemaining.compareTo(EPSILON) > 0) {
                creditors.add(new Node(creditor.userId(), creditor.name(), creditorRemaining));
            }
            if (debtorRemaining.compareTo(EPSILON) > 0) {
                debtors.add(new Node(debtor.userId(), debtor.name(), debtorRemaining));
            }
        }

        return transactions;
    }
}
