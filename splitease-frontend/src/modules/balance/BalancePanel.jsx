import { useEffect, useState } from "react";
import { balanceService } from "./balanceService";

export default function BalancePanel({ groupId }) {
  const [balances, setBalances] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    balanceService
      .getGroupBalances(groupId)
      .then(setBalances)
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, [groupId]);

  if (loading) return <p>Loading…</p>;
  if (error) return <p className="error-text">{error}</p>;
  if (balances.length === 0) return <div className="empty-state"><p>No balances yet.</p></div>;

  return (
    <div className="card">
      <table className="ledger">
        <thead>
          <tr>
            <th>Member</th>
            <th style={{ textAlign: "right" }}>Paid</th>
            <th style={{ textAlign: "right" }}>Owed</th>
            <th style={{ textAlign: "right" }}>Net balance</th>
          </tr>
        </thead>
        <tbody>
          {balances.map((b) => {
            const net = b.netBalance ?? b.totalPaid - b.totalOwed;
            const status = net > 0.001 ? "receive" : net < -0.001 ? "owe" : "settled";
            return (
              <tr key={b.userId}>
                <td>{b.userName}</td>
                <td className="num" style={{ textAlign: "right" }}>₹{Number(b.totalPaid).toFixed(2)}</td>
                <td className="num" style={{ textAlign: "right" }}>₹{Number(b.totalOwed).toFixed(2)}</td>
                <td style={{ textAlign: "right" }}>
                  <span className={status === "settled" ? "num" : `num amount-${status}`}>
                    {net > 0 ? "+" : ""}
                    ₹{Number(net).toFixed(2)}
                  </span>{" "}
                  <span className={`pill ${status}`}>
                    {status === "receive" ? "gets back" : status === "owe" ? "owes" : "settled"}
                  </span>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
