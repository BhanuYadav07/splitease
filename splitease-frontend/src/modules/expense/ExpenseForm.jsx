import { useMemo, useState } from "react";
import { expenseService } from "./expenseService";

const SPLIT_TYPES = [
  { key: "EQUAL", label: "Equal" },
  { key: "UNEQUAL", label: "Unequal" },
  { key: "PERCENTAGE", label: "Percentage" },
];

function distributeEqual(total, participantIds) {
  // Deterministic handling of indivisible minor units: base share + remainder
  // paise/cents distributed to the first N participants (sorted by id).
  const n = participantIds.length;
  if (n === 0) return {};
  const totalMinor = Math.round(total * 100);
  const base = Math.floor(totalMinor / n);
  const remainder = totalMinor - base * n;
  const sorted = [...participantIds].sort();
  const shares = {};
  sorted.forEach((id, idx) => {
    const minor = base + (idx < remainder ? 1 : 0);
    shares[id] = minor / 100;
  });
  return shares;
}

export default function ExpenseForm({ group, onCreated }) {
  const members = group.members || [];
  const [description, setDescription] = useState("");
  const [amount, setAmount] = useState("");
  const [payerId, setPayerId] = useState(members[0]?.id || "");
  const [splitType, setSplitType] = useState("EQUAL");
  const [selected, setSelected] = useState(() => new Set(members.map((m) => m.id)));
  const [customValues, setCustomValues] = useState({}); // memberId -> string amount or percent
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const total = parseFloat(amount) || 0;
  const participantIds = useMemo(() => members.filter((m) => selected.has(m.id)).map((m) => m.id), [
    members,
    selected,
  ]);

  const equalShares = useMemo(() => distributeEqual(total, participantIds), [total, participantIds]);

  function toggleParticipant(id) {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  function setCustom(id, value) {
    setCustomValues((prev) => ({ ...prev, [id]: value }));
  }

  const unequalSum = participantIds.reduce((s, id) => s + (parseFloat(customValues[id]) || 0), 0);
  const percentageSum = participantIds.reduce((s, id) => s + (parseFloat(customValues[id]) || 0), 0);

  function buildShares() {
    if (splitType === "EQUAL") {
      return participantIds.map((id) => ({ userId: id, amount: equalShares[id] }));
    }
    if (splitType === "UNEQUAL") {
      return participantIds.map((id) => ({ userId: id, amount: parseFloat(customValues[id]) || 0 }));
    }
    // PERCENTAGE: the API expects each participant's percentage (0-100) and
    // performs the minor-unit rounding (largest-remainder) on the server, so
    // send the raw percentages the user entered rather than pre-computed
    // rupee amounts.
    return participantIds.map((id) => ({ userId: id, amount: parseFloat(customValues[id]) || 0 }));
  }

  function validate() {
    if (!description.trim()) return "Description is required.";
    if (!(total > 0)) return "Amount must be greater than zero.";
    if (!payerId) return "Select a payer.";
    if (participantIds.length === 0) return "Select at least one participant.";
    if (new Set(participantIds).size !== participantIds.length) return "Duplicate participants are not allowed.";
    if (splitType === "UNEQUAL") {
      if (Math.abs(unequalSum - total) > 0.01) {
        return `Shares must sum to ${total.toFixed(2)} (currently ${unequalSum.toFixed(2)}).`;
      }
    }
    if (splitType === "PERCENTAGE") {
      if (Math.abs(percentageSum - 100) > 0.01) {
        return `Percentages must total 100% (currently ${percentageSum.toFixed(1)}%).`;
      }
    }
    return "";
  }

  async function handleSubmit(e) {
    e.preventDefault();
    const validationError = validate();
    if (validationError) {
      setError(validationError);
      return;
    }
    setError("");
    setSubmitting(true);
    try {
      await expenseService.createExpense(group.id, {
        description,
        amount: total,
        payerId,
        splitType,
        shares: buildShares(),
      });
      onCreated?.();
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit}>
      <div className="field">
        <label htmlFor="desc">Description</label>
        <input id="desc" required value={description} onChange={(e) => setDescription(e.target.value)} placeholder="e.g. Dinner at Cafe" />
      </div>

      <div style={{ display: "flex", gap: 14 }}>
        <div className="field" style={{ flex: 1 }}>
          <label htmlFor="amount">Total amount</label>
          <input
            id="amount"
            type="number"
            step="0.01"
            min="0.01"
            required
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
          />
        </div>
        <div className="field" style={{ flex: 1 }}>
          <label htmlFor="payer">Paid by</label>
          <select id="payer" value={payerId} onChange={(e) => setPayerId(e.target.value)}>
            {members.map((m) => (
              <option key={m.id} value={m.id}>
                {m.name}
              </option>
            ))}
          </select>
        </div>
      </div>

      <div className="field">
        <label>Split type</label>
        <div className="split-tabs">
          {SPLIT_TYPES.map((s) => (
            <button
              type="button"
              key={s.key}
              className={splitType === s.key ? "active" : ""}
              onClick={() => setSplitType(s.key)}
            >
              {s.label}
            </button>
          ))}
        </div>
      </div>

      <div className="field">
        <label>Participants</label>
        <div className="card" style={{ padding: "8px 16px" }}>
          {members.map((m) => {
            const isChecked = selected.has(m.id);
            return (
              <div className="participant-row" key={m.id}>
                <input
                  type="checkbox"
                  checked={isChecked}
                  onChange={() => toggleParticipant(m.id)}
                  style={{ width: "auto" }}
                />
                <span className="name">{m.name}</span>
                {isChecked && splitType === "EQUAL" && (
                  <span className="num" style={{ color: "var(--ink-soft)" }}>
                    ₹{(equalShares[m.id] ?? 0).toFixed(2)}
                  </span>
                )}
                {isChecked && splitType === "UNEQUAL" && (
                  <input
                    type="number"
                    step="0.01"
                    placeholder="₹0.00"
                    value={customValues[m.id] || ""}
                    onChange={(e) => setCustom(m.id, e.target.value)}
                  />
                )}
                {isChecked && splitType === "PERCENTAGE" && (
                  <input
                    type="number"
                    step="0.1"
                    placeholder="0%"
                    value={customValues[m.id] || ""}
                    onChange={(e) => setCustom(m.id, e.target.value)}
                  />
                )}
              </div>
            );
          })}
        </div>
        {splitType === "UNEQUAL" && (
          <p style={{ fontSize: 12.5, color: "var(--ink-soft)", marginTop: 6 }}>
            Sum: ₹{unequalSum.toFixed(2)} of ₹{total.toFixed(2)}
          </p>
        )}
        {splitType === "PERCENTAGE" && (
          <p style={{ fontSize: 12.5, color: "var(--ink-soft)", marginTop: 6 }}>
            Total: {percentageSum.toFixed(1)}% of 100%
          </p>
        )}
      </div>

      {error && <p className="error-text">{error}</p>}

      <button className="btn" type="submit" disabled={submitting}>
        {submitting ? "Saving…" : "Save expense"}
      </button>
    </form>
  );
}
