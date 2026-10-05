import { api } from "../common/api";

export const settlementService = {
  getSuggestedSettlements: (groupId) => api.get(`/groups/${groupId}/settlements/suggested`),
  getSettlementHistory: (groupId) => api.get(`/groups/${groupId}/settlements`),
  recordSettlement: (groupId, payload) => api.post(`/groups/${groupId}/settlements`, payload),
};
