# notification-service-medicology

Spring Boot notification service scaffold with OpenAPI/Swagger enabled.

## Stack
- Java 17
- Spring Boot 3.3.5
- Maven Wrapper
- springdoc-openapi 2.6.0
-
## Run locally
1. Windows: `./mvnw.cmd spring-boot:run`
2. macOS/Linux: `./mvnw spring-boot:run`

App starts at `http://localhost:8080`.

## API Docs
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

## Health endpoint
- `GET /api/health`

## Build and test
- Windows: `./mvnw.cmd clean test`
- macOS/Linux: `./mvnw clean test`
