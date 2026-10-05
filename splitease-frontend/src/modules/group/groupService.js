import { api } from "../common/api";

export const groupService = {
  listGroups: () => api.get("/groups"),
  getGroup: (groupId) => api.get(`/groups/${groupId}`),
  createGroup: (payload) => api.post("/groups", payload),
  addMember: (groupId, payload) => api.post(`/groups/${groupId}/members`, payload),
  removeMember: (groupId, userId) => api.delete(`/groups/${groupId}/members/${userId}`),
};
