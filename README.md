# PayFlow – Async Payment Processing System (Java/Spring Boot)

An idempotent payment order service built with **Java 17**, **Spring Boot 3**, and **RabbitMQ**.

> 💷 All examples and tests use **GBP**

---

## 🚀 Features

- ✅ **Idempotent Order Creation** – Safe retries with `Idempotency-Key`
- ✅ **Async Payment Processing** – Orders accepted instantly; payments handled in background via RabbitMQ
- ✅ **PostgreSQL persistence** – Uses a production-grade relational database for all order data
- ✅ **Full Test Coverage** – Unit (Mockito) + Integration (Testcontainers-ready)
- ✅ **Docker-First** – One-command setup with PostgreSQL + RabbitMQ

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
| Build          | Maven                         |

---

## ▶️ Run Locally

1. **Start dependencies** (PostgreSQL + RabbitMQ):
   ```bash
   docker-compose up -d