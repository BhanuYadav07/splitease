const BASE_URL = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080/api";

function getToken() {
  return localStorage.getItem("splitease_jwt");
}

async function request(path, { method = "GET", body, auth = true } = {}) {
  const headers = { "Content-Type": "application/json" };
  if (auth) {
    const token = getToken();
    if (token) headers["Authorization"] = `Bearer ${token}`;
  }
  const res = await fetch(`${BASE_URL}${path}`, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  });
  if (res.status === 401 && auth) {
    // Only an authenticated call can expire a session. A 401 from a public one - login,
    // register, forgot/reset password - is just a wrong credential, and hard-redirecting
    // there threw the server's message away before the page could show it.
    localStorage.removeItem("splitease_jwt");
    window.location.href = "/login";
    throw new Error("Session expired. Please log in again.");
  }
  const isJson = res.headers.get("content-type")?.includes("application/json");
  const data = isJson ? await res.json().catch(() => null) : null;
  if (!res.ok) {
    const message = data?.message || data?.error || `Request failed (${res.status})`;
    throw new Error(message);
  }
  return data;
}

export const api = {
  get: (path) => request(path),
  post: (path, body, opts = {}) => request(path, { method: "POST", body, ...opts }),
  put: (path, body) => request(path, { method: "PUT", body }),
  patch: (path, body) => request(path, { method: "PATCH", body }),
  delete: (path) => request(path, { method: "DELETE" }),
};

export { getToken };
