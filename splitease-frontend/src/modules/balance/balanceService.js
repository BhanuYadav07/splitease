import { api } from "../common/api";

export const balanceService = {
  getGroupBalances: (groupId) => api.get(`/groups/${groupId}/balances`),
  getMyBalance: (groupId) => api.get(`/groups/${groupId}/balances/me`),
};
