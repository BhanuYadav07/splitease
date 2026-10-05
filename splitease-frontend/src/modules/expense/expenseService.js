import { api } from "../common/api";

export const expenseService = {
  listExpenses: (groupId) => api.get(`/groups/${groupId}/expenses`),
  getExpense: (groupId, expenseId) => api.get(`/groups/${groupId}/expenses/${expenseId}`),
  createExpense: (groupId, payload) => api.post(`/groups/${groupId}/expenses`, payload),
  updateExpense: (groupId, expenseId, payload) =>
    api.put(`/groups/${groupId}/expenses/${expenseId}`, payload),
  deleteExpense: (groupId, expenseId) => api.delete(`/groups/${groupId}/expenses/${expenseId}`),
};
