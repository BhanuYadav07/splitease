import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { groupService } from "./groupService";
import { expenseService } from "../expense/expenseService";
import ExpenseForm from "../expense/ExpenseForm";
import ExpenseList from "../expense/ExpenseList";
import BalancePanel from "../balance/BalancePanel";
import SettlementPanel from "../settlement/SettlementPanel";

const TABS = ["Expenses", "Balances", "Settlements", "Members"];

export default function GroupDetailPage() {
  const { groupId } = useParams();
  const [group, setGroup] = useState(null);
  const [expenses, setExpenses] = useState([]);
  const [tab, setTab] = useState("Expenses");
  const [showExpenseForm, setShowExpenseForm] = useState(false);
  const [error, setError] = useState("");
  const [memberEmail, setMemberEmail] = useState("");

  function loadGroup() {
    groupService.getGroup(groupId).then(setGroup).catch((err) => setError(err.message));
  }

  function loadExpenses() {
    expenseService.listExpenses(groupId).then(setExpenses).catch((err) => setError(err.message));
  }

  useEffect(() => {
    loadGroup();
    loadExpenses();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [groupId]);

  async function handleAddMember(e) {
    e.preventDefault();
    try {
      await groupService.addMember(groupId, { email: memberEmail });
      setMemberEmail("");
      loadGroup();
    } catch (err) {
      setError(err.message);
    }
  }

  async function handleRemoveMember(userId) {
    try {
      await groupService.removeMember(groupId, userId);
      loadGroup();
    } catch (err) {
      setError(err.message);
    }
  }

  if (!group) return <p>Loading…</p>;

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>{group.name}</h1>
          <p>{group.members?.length ?? 0} members</p>
        </div>
        <button className="btn" onClick={() => setShowExpenseForm((s) => !s)}>
          {showExpenseForm ? "Cancel" : "Add expense"}
        </button>
      </div>

      {error && <p className="error-text">{error}</p>}

      {showExpenseForm && (
        <div className="card" style={{ marginBottom: 20 }}>
          <ExpenseForm
            group={group}
            onCreated={() => {
              setShowExpenseForm(false);
              loadExpenses();
            }}
          />
        </div>
      )}

      <div className="split-tabs" style={{ maxWidth: 480, marginBottom: 20 }}>
        {TABS.map((t) => (
          <button key={t} className={tab === t ? "active" : ""} onClick={() => setTab(t)}>
            {t}
          </button>
        ))}
      </div>

      {tab === "Expenses" && (
        <ExpenseList expenses={expenses} group={group} onChanged={loadExpenses} />
      )}

      {tab === "Balances" && <BalancePanel groupId={groupId} members={group.members} />}

      {tab === "Settlements" && <SettlementPanel groupId={groupId} members={group.members} />}

      {tab === "Members" && (
        <div className="card">
          <table className="ledger">
            <thead>
              <tr>
                <th>Name</th>
                <th>Email</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {group.members?.map((m) => (
                <tr key={m.id}>
                  <td>{m.name}</td>
                  <td>{m.email}</td>
                  <td style={{ textAlign: "right" }}>
                    <button className="btn secondary" onClick={() => handleRemoveMember(m.id)}>
                      Remove
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <form onSubmit={handleAddMember} style={{ display: "flex", gap: 8, marginTop: 16 }}>
            <input
              placeholder="Member email"
              type="email"
              required
              value={memberEmail}
              onChange={(e) => setMemberEmail(e.target.value)}
              style={{ flex: 1, border: "1px solid var(--line-strong)", borderRadius: 3, padding: "8px 10px" }}
            />
            <button className="btn" type="submit">
              Add member
            </button>
          </form>
        </div>
      )}
    </div>
  );
}
