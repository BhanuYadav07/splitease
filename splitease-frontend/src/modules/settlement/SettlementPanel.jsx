import { useEffect, useState } from "react";
import { settlementService } from "./settlementService";

export default function SettlementPanel({ groupId }) {
  const [suggested, setSuggested] = useState([]);
  const [history, setHistory] = useState([]);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [recordingKey, setRecordingKey] = useState(null);

  function load() {
    setLoading(true);
    Promise.all([
      settlementService.getSuggestedSettlements(groupId),
      settlementService.getSettlementHistory(groupId),
    ])
      .then(([s, h]) => {
        setSuggested(s || []);
        setHistory(h || []);
      })
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }

  useEffect(load, [groupId]);

  async function handleRecord(s) {
    const key = `${s.fromUserId}-${s.toUserId}`;
    setRecordingKey(key);
    try {
      await settlementService.recordSettlement(groupId, {
        fromUserId: s.fromUserId,
        toUserId: s.toUserId,
        amount: s.amount,
      });
      load();
    } catch (err) {
      setError(err.message);
    } finally {
      setRecordingKey(null);
    }
  }

  if (loading) return <p>Loading…</p>;

  return (
    <div>
      {error && <p className="error-text">{error}</p>}

      <h3 style={{ fontSize: 15, textTransform: "uppercase", letterSpacing: ".03em", color: "var(--ink-soft)", fontFamily: "var(--font-body)", fontWeight: 600 }}>
        Suggested settlements
      </h3>
      {suggested.length === 0 ? (
        <div className="empty-state" style={{ marginBottom: 20 }}>
          <p>Everyone is settled up.</p>
        </div>
      ) : (
        <div className="card" style={{ marginBottom: 20 }}>
          <table className="ledger">
            <thead>
              <tr>
                <th>From</th>
                <th>To</th>
                <th style={{ textAlign: "right" }}>Amount</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {suggested.map((s) => {
                const key = `${s.fromUserId}-${s.toUserId}`;
                return (
                  <tr key={key}>
                    <td>{s.fromUserName}</td>
                    <td>{s.toUserName}</td>
                    <td className="num amount-owe" style={{ textAlign: "right" }}>
                      ₹{Number(s.amount).toFixed(2)}
                    </td>
                    <td style={{ textAlign: "right" }}>
                      <button
                        className="btn secondary"
                        disabled={recordingKey === key}
                        onClick={() => handleRecord(s)}
                      >
                        {recordingKey === key ? "Recording…" : "Mark as paid"}
                      </button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      <h3 style={{ fontSize: 15, textTransform: "uppercase", letterSpacing: ".03em", color: "var(--ink-soft)", fontFamily: "var(--font-body)", fontWeight: 600 }}>
        Settlement history
      </h3>
      {history.length === 0 ? (
        <div className="empty-state">
          <p>No settlements recorded yet.</p>
        </div>
      ) : (
        <div className="card">
          <table className="ledger">
            <thead>
              <tr>
                <th>From</th>
                <th>To</th>
                <th style={{ textAlign: "right" }}>Amount</th>
                <th>Status</th>
                <th>Date</th>
              </tr>
            </thead>
            <tbody>
              {history.map((h) => (
                <tr key={h.id}>
                  <td>{h.fromUserName}</td>
                  <td>{h.toUserName}</td>
                  <td className="num" style={{ textAlign: "right" }}>₹{Number(h.amount).toFixed(2)}</td>
                  <td>
                    <span className="pill settled">{h.status}</span>
                  </td>
                  <td style={{ color: "var(--ink-soft)", fontSize: 13 }}>
                    {h.settledAt ? new Date(h.settledAt).toLocaleDateString() : "—"}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
