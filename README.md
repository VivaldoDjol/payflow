# PayFlow – Async Payment Processing System (Java/Spring Boot)

An idempotent payment order service built with **Java 17**, **Spring Boot 3**, and **RabbitMQ**.

---

## 🚀 Features

- ✅ **Idempotent Order Creation** – Safe retries with `Idempotency-Key`
- ✅ **Async Payment Processing** – Orders accepted instantly; payments handled in background via RabbitMQ
- ✅ **PostgreSQL persistence** – Uses a production-grade relational database for all order data
- ✅ **Full Test Coverage** – Unit (Mockito) + Integration (Testcontainers-ready)
- ✅ **Docker-First** – One-command setup with PostgreSQL + RabbitMQ
- ✅ **Comprehensive API Documentation** – Interactive Swagger UI with detailed examples
- ✅ **Production-ready Observability** – Health checks, metrics, and logging

---

## 🛠️ Tech Stack

| Layer          | Technology                    |
|----------------|-------------------------------|
| Language       | Java 17                       |
| Framework      | Spring Boot 3.5               |
| Web            | Spring Web, Validation        |
| Data           | Spring Data JPA, PostgreSQL   |
| Messaging      | RabbitMQ (via Spring AMQP)    |
| Testing        | JUnit 5, Mockito, AssertJ     |
| DevOps         | Docker, Docker Compose        |
| Documentation  | SpringDoc OpenAPI (Swagger)   |
| Observability  | Spring Boot Actuator          |
| Build          | Maven                         |

---

## 📚 API Documentation

Interactive API documentation available at:
- **API Documentation**: `http://localhost:8080/swagger-ui.html`
- **API Docs JSON**: `http://localhost:8080/v3/api-docs`

Endpoints include:
- `POST /orders` - Create payment order (idempotent)
- `GET /orders/{id}` - Retrieve order by ID
- Actuator endpoints for health and metrics

---

## ▶️ Run Locally

1. **Start dependencies** (PostgreSQL + RabbitMQ):
   ```bash
   docker-compose up -d
   ```

2. **Build and run the application**:
   ```bash
   ./mvnw spring-boot:run
   ```

   Or with Docker:
   ```bash
   docker-compose up -d --build
   ```

3. **Access the application**:
- **API**: `http://localhost:8080`
- **API Documentation**: `http://localhost:8080/swagger-ui/index.html`
- **API Docs JSON**: `http://localhost:8080/v3/api-docs`
- **Health Check**: `http://localhost:8080/actuator/health`
- **RabbitMQ Management**: `http://localhost:15672` (guest/guest)

---

## 🧪 Testing

Run unit and integration tests:
```bash
./mvnw test