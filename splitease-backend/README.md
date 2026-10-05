# SplitEase Backend

Spring Boot 3 / Java 17 backend implementing the SplitEase functional requirements.

## Package structure (matches the project diagram)

```
com.splitease
├── auth        AuthController, AuthService, JwtService
├── user        User, UserRepository, UserService, UserController
├── group       Group, GroupMember, GroupController, GroupService
├── expense     Expense, ExpenseSplit, ExpenseController, ExpenseService
├── balance     BalanceService, BalanceController
├── settlement  Settlement, SettlementService, CashFlowAlgorithm, SettlementController
├── security    SecurityConfig, JwtFilter, AppUserDetailsService, CurrentUserProvider
├── exception   GlobalExceptionHandler + domain exceptions
└── common      BaseEntity (id/timestamps/@Version), ApiError
```

## Run locally

Default profile uses an in-memory H2 database, so no external setup is needed:

```bash
mvn spring-boot:run
```

- API base: `http://localhost:8080/api`
- H2 console: `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:mem:splitease`)

To use MySQL instead:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
# and set DB_URL / DB_USERNAME / DB_PASSWORD env vars
# e.g. DB_URL=jdbc:mysql://localhost:3306/splitease
```

The `mysql` profile expects a database named `splitease` to already exist:

```sql
CREATE DATABASE splitease CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

Set a real `JWT_SECRET` env var (32+ bytes) in any non-local environment.

## Key design notes, mapped to the functional requirements

- **Auth (1):** BCrypt password hashing, stateless JWT (`JwtService`/`JwtFilter`), `/api/auth/**` is the only public path.
- **Groups (2) & authorization (10):** every group/expense/balance/settlement endpoint calls `GroupService.requireMembership()` first, so a user can never reach another group's data by editing the ID in the URL. Removing a member is blocked while they have a non-zero net balance.
- **Splits (3-6, 11):** `ExpenseService` validates payer/participants are group members, rejects duplicate participants, and computes each split type so shares always reconcile exactly to the total:
  - EQUAL: divides in integer minor units (paise/cents) and hands the leftover unit to the first participants (sorted by ID) — deterministic and exact.
  - UNEQUAL: requires the caller-supplied amounts to already sum to the total (within a 1-cent tolerance).
  - PERCENTAGE: requires percentages to total 100%, then uses the largest-remainder method so rounding never leaves the total off by a cent.
- **Balances (7):** `BalanceService` computes `Total Paid − Total Owed` per member from expenses, adjusted by any settlements already recorded as paid.
- **Debt simplification (8):** `CashFlowAlgorithm` uses two max-heaps (`PriorityQueue`) of creditors/debtors, greedily matching the largest creditor against the largest debtor each round — the standard cash-flow minimization approach.
- **Settlements (9):** recording a settlement validates a positive amount, that both users are group members, and that a user isn't settling with themselves; history is stored in the `settlement` table.
- **Concurrency (12):** `BaseEntity` carries a JPA `@Version` column for optimistic locking on groups/expenses/settlements; expense creation/update/delete are `@Transactional` so a mid-way failure rolls back completely. `GlobalExceptionHandler` maps lock conflicts to `409 Conflict`.

## Example flow

1. `POST /api/auth/register`, then `POST /api/auth/login` → JWT.
2. `POST /api/groups` to create a group (creator becomes admin).
3. `POST /api/groups/{groupId}/members` to add members by email.
4. `POST /api/groups/{groupId}/expenses` with a `splitType` of `EQUAL` / `UNEQUAL` / `PERCENTAGE`.
5. `GET /api/groups/{groupId}/balances` for net balances.
6. `GET /api/groups/{groupId}/settlements/suggested` for the simplified payment plan, `POST /api/groups/{groupId}/settlements` to record one as paid.
