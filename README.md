# Balance Challenge App

A Java 21 / Spring Boot service

## Technology

- Java 21 and Spring Boot 4
- PostgreSQL 17
- Spring Data JPA plus small, explicit JDBC statements for operation claiming
- Liquibase migrations
- Springdoc OpenAPI / Swagger UI
- JUnit 5 and Testcontainers with real PostgreSQL
- Docker and Docker Compose


##  design

Every credit, debit, or transfer runs in one `READ_COMMITTED` database transaction:

1. The request atomically claims its `transactionId` with `INSERT ... ON CONFLICT DO NOTHING`.
2. A unique constraint on `balance_operation.transaction_id` permits only one owner.
3. PostgreSQL assigns a monotonic fencing token. Completion succeeds only when both the transaction ID and fencing token match a still-`PENDING` operation.
4. The affected account row is locked with `SELECT ... FOR UPDATE` before its balance changes.
5. Transfers lock both accounts in ascending account-ID order, regardless of transfer direction. This stable lock ordering prevents the usual A-to-B / B-to-A deadlock.
6. The account entity also has a JPA `@Version` column as a secondary lost-update guard.
7. The operation result and balance changes commit together. A failure rolls both back.

An exact retry with the same transaction ID and payload returns the stored result without applying the balance effect again. Reusing a transaction ID with a different operation, account, or amount returns HTTP `409 Conflict`.

Balances and amounts use integer `long` values. Negative balances and non-positive operation amounts are also protected by database constraints.

### Decision Trade of design

1. For unknown account because of security and sensetive  stored error in db
2. If source and destination is same service throw exception `Source and destination accounts must be different`

## Run locally

Prerequisite: Docker with Compose support.


Start only PostgreSQL if desired:

```bash
docker compose up -d postgres
./mvnw spring-boot:run
```

## Run with builded Version
```bash
docker compose up -d postgres
java -jar target/balance-challenge-0.0.1-SNAPSHOT.jar

```


### Access Swagger Ui:

[Balance Swagger http://127.0.0.1:8080/swagger-ui/index.html](http://127.0.0.1:8080/swagger-ui/index.html)

Database settings can be overridden with `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`.
Hibernate SQL and bind values are logged by default. Set `SQL_LOG_LEVEL=OFF` and
`SQL_BIND_LOG_LEVEL=OFF` to disable them, for example in production.

## Tests

Docker must be running because integration tests use Testcontainers:

```bash
./mvnw test
```

The test suite calls the service layer directly and covers:

- normal credit, debit, transfer, rejection, and replay behavior;
- transaction-ID payload conflicts;
- concurrent debits that must not overspend;
- 32 simultaneous duplicates with exactly one balance effect;
- concurrent unique credits without lost updates;
- opposite-direction transfers with stable lock ordering and invariant total balance.

## Project structure

- `account`: account entity and locking repository
- `balance`: transactional application service, operation claim, and domain result
- `api`: REST controllers, DTOs, error mapping, and OpenAPI metadata
- `db/changelog`: Liquibase schema migrations
- `src/test`: PostgreSQL integration and concurrency tests


The application waits for PostgreSQL to become healthy. Liquibase applies the schema automatically.

- API: `http://localhost:8080/api/v1`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Health: `http://localhost:8080/actuator/health`

Stop the containers while preserving database data:

```bash
docker compose down
```



## API examples

Create two accounts:

```bash
curl -i -X POST http://localhost:8080/api/v1/accounts \
  -H 'Content-Type: application/json' \
  -d '{"accountId":"A"}'

curl -i -X POST http://localhost:8080/api/v1/accounts \
  -H 'Content-Type: application/json' \
  -d '{"accountId":"B"}'
```

Credit account A:

```bash
curl -i -X POST http://localhost:8080/api/v1/transactions/credits \
  -H 'Content-Type: application/json' \
  -d '{"accountId":"A","amount":1000,"transactionId":"TX-100"}'
```

Debit account A:

```bash
curl -i -X POST http://localhost:8080/api/v1/transactions/debits \
  -H 'Content-Type: application/json' \
  -d '{"accountId":"A","amount":200,"transactionId":"TX-101"}'
```

Transfer from A to B:

```bash
curl -i -X POST http://localhost:8080/api/v1/transactions/transfers \
  -H 'Content-Type: application/json' \
  -d '{"sourceAccountId":"A","destinationAccountId":"B","amount":300,"transactionId":"TX-102"}'
```

Read a committed balance:

```bash
curl -i http://localhost:8080/api/v1/accounts/A/balance
```
