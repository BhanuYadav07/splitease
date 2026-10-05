import { useState } from "react";
import { expenseService } from "./expenseService";

export default function ExpenseList({ expenses, group, onChanged }) {
  const [error, setError] = useState("");
  const [deletingId, setDeletingId] = useState(null);

  async function handleDelete(expenseId) {
    setDeletingId(expenseId);
    try {
      await expenseService.deleteExpense(group.id, expenseId);
      onChanged?.();
    } catch (err) {
      setError(err.message);
    } finally {
      setDeletingId(null);
    }
  }

  if (!expenses || expenses.length === 0) {
    return (
      <div className="empty-state">
        <p>No expenses recorded yet.</p>
      </div>
    );
  }

  return (
    <div className="card">
      {error && <p className="error-text">{error}</p>}
      <table className="ledger">
        <thead>
          <tr>
            <th>Description</th>
            <th>Paid by</th>
            <th>Split</th>
            <th style={{ textAlign: "right" }}>Amount</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {expenses.map((exp) => (
            <tr key={exp.id}>
              <td>{exp.description}</td>
              <td>{exp.payerName || exp.payer?.name}</td>
              <td style={{ textTransform: "capitalize" }}>{(exp.splitType || "").toLowerCase()}</td>
              <td className="num" style={{ textAlign: "right" }}>
                ₹{Number(exp.amount).toFixed(2)}
              </td>
              <td style={{ textAlign: "right" }}>
                <button
                  className="btn secondary"
                  disabled={deletingId === exp.id}
                  onClick={() => handleDelete(exp.id)}
                >
                  {deletingId === exp.id ? "Removing…" : "Delete"}
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
