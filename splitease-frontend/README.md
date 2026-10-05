# SplitEase Frontend

React + Vite frontend for the SplitEase expense-splitting backend (`com.splitease`).
The folder structure under `src/modules/` mirrors the backend's package structure so each
domain (auth, user, group, expense, balance, settlement) has its matching service + pages
on both sides:

```
com.splitease (backend)        src/modules (frontend)
├── auth                       ├── auth        (AuthContext, LoginPage, RegisterPage, ProtectedRoute, authService)
├── user                       ├── user        (ProfilePage, userService)
├── group                      ├── group       (GroupsPage, GroupDetailPage, groupService)
├── expense                    ├── expense     (ExpenseForm, ExpenseList, expenseService)
├── balance                    ├── balance     (BalancePanel, balanceService)
├── settlement                 ├── settlement  (SettlementPanel, settlementService)
└── security                   └── common      (api.js — attaches the JWT, handles 401s)
```

`security`/`exception` on the backend map to `common/api.js` on the frontend: every request
attaches the JWT from `localStorage`, and a 401 response clears the session and redirects to
`/login`, mirroring the backend's JWT filter + global exception handler behavior.

## Getting started

```bash
npm install
cp .env.example .env      # point VITE_API_BASE_URL at your Spring Boot backend
npm run dev
```

The app expects the backend's REST API at `VITE_API_BASE_URL` (default
`http://localhost:8080/api`), matching the endpoints implied by the functional requirements:

- `POST /auth/register`, `POST /auth/login`
- `GET/PUT /users/me`
- `GET/POST /groups`, `GET /groups/:id`, `POST/DELETE /groups/:id/members`
- `GET/POST/PUT/DELETE /groups/:id/expenses`
- `GET /groups/:id/balances`
- `GET /groups/:id/settlements/suggested`, `GET/POST /groups/:id/settlements`

## Notes on split logic

`ExpenseForm.jsx` implements all three split types client-side, matching the functional spec:

- **Equal**: divides the total into minor units (paise) and distributes the remainder to the
  first N participants (sorted by id) so shares always sum exactly to the total.
- **Unequal**: validates entered shares sum to the total (within 1 paise tolerance).
- **Percentage**: validates percentages sum to 100%, then allocates minor units by largest
  remainder so the final rupee amounts sum exactly to the total.

## Build

```bash
npm run build
```

Outputs a static bundle to `dist/`, deployable to any static host (or served behind the same
reverse proxy as the Spring Boot API).
