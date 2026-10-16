A full-stack expense sharing and debt simplification application built
with Spring Boot and React.

SplitEase helps users create groups, record shared expenses, calculate
balances, and simplify settlements so that fewer transactions are
required to settle debts.
✨ Features
- 👤 User registration and login
- 🔐 JWT-based authentication
- 🔑 Password hashing with BCrypt
- 📧 Email verification / OTP-based verification
- 🔄 Forgot-password and password-reset flow
- 👥 Create and manage expense groups
- 💰 Add shared expenses
- ⚖️ Support for:
  - Equal split
  - Unequal/fixed-amount split
  - Percentage-based split
- 📊 Calculate individual balances
- 💸 Debt simplification using a cash-flow settlement algorithm
- 🔒 Protected API endpoints with Spring Security
- 🗄️ Persistent data storage using MySQL
- 🌐 REST APIs for frontend-backend communication
- 📱 React-based frontend
🏗️ Project Structure
splitease/
│
├── splitease-backend/
│   ├── src/
│   │   └── main/
│   │       ├── java/
│   │       └── resources/
│   ├── pom.xml
│   └── Dockerfile
│
├── splitease-frontend/
│   ├── src/
│   ├── public/
│   ├── package.json
│   └── vite.config.js
│
└── README.md
🛠️ Tech Stack
Backend
- Java 17
- Spring Boot 3
- Spring Web
- Spring Data JPA
- Hibernate
- Spring Security
- JWT
- BCrypt
- MySQL
- Maven
- REST API
- Docker
Frontend
- React
- JavaScript
- Vite
- HTML5
- CSS3
- Axios / Fetch for API communication
🧩 Architecture
┌──────────────────────┐
│   React Frontend     │
│  splitease-frontend  │
└──────────┬───────────┘
           │ HTTP / REST API
           ▼
┌──────────────────────┐
│   Spring Boot API    │
│  splitease-backend   │
└──────────┬───────────┘
           │ JPA / Hibernate
           ▼
┌──────────────────────┐
│       MySQL          │
│      Database        │
└──────────────────────┘
💸 Debt Simplification
SplitEase calculates how much each member owes or should receive and
then simplifies the resulting debts.
For example, instead of:
A → B ₹500
A → C ₹300
D → C ₹200
D → B ₹100
the settlement algorithm attempts to reduce the number of transactions
by matching people who need to pay with people who need to receive.
This is implemented using a cash-flow/debt-simplification approach with
priority queues.
🔐 Authentication
The backend uses Spring Security with JWT-based authentication.
Typical authentication flow:
Register
   ↓
Verify account
   ↓
Login
   ↓
JWT issued
   ↓
Client sends JWT with protected requests
   ↓
Spring Security validates JWT
   ↓
Request reaches controller
Protected requests use the Bearer token format:
Authorization: Bearer <JWT_TOKEN>
🚀 Getting Started
Prerequisites
Install the following:
- Java 17+
- Maven
- Node.js 18+
- npm
- MySQL 8+
- Git
1. Clone the repository
git clone https://github.com/BhanuYadav07/splitease.git
cd splitease
2. Start the backend
cd splitease-backend
Configure your database credentials in your application configuration or
environment variables.
Example:
spring.datasource.url=jdbc:mysql://localhost:3306/splitease
spring.datasource.username=root
spring.datasource.password=YOUR_PASSWORD

spring.jpa.hibernate.ddl-auto=update
Then run:
mvn spring-boot:run
The backend will normally start on:
http://localhost:8080
3. Start the frontend
Open another terminal:
cd splitease/splitease-frontend
Install dependencies:
npm install
Start the development server:
npm run dev
Vite will display the local frontend URL in the terminal, usually:
http://localhost:5173
⚙️ Environment Variables
Do not commit passwords, JWT secrets, API keys, or other credentials to
GitHub.
Example backend environment variables:
DB_URL=jdbc:mysql://localhost:3306/splitease
DB_USERNAME=root
DB_PASSWORD=your_password
JWT_SECRET=your_secret
Example frontend environment variable:
VITE_API_URL=http://localhost:8080
Use .env files locally and keep them out of Git using .gitignore.
🧪 Testing
Backend tests can be run with:
cd splitease-backend
mvn test
For frontend development:
cd splitease-frontend
npm run dev
🐳 Docker
The backend can also be containerized using Docker.
Build the backend image:
cd splitease-backend
docker build -t splitease-backend .
Run the container:
docker run -p 8080:8080 splitease-backend
If using Docker Compose, make sure the backend connects to the MySQL
service using the Docker service name rather than localhost.
📌 Main Modules
Authentication
     │
     ├── Registration
     ├── Login
     ├── Verification
     └── Password Reset

Groups
     │
     ├── Create Group
     ├── Add Members
     └── Group Expenses

Expenses
     │
     ├── Equal Split
     ├── Unequal Split
     └── Percentage Split

Balances
     │
     ├── Calculate balances
     └── Simplify debts

Settlement
     │
     └── Generate optimized transactions
