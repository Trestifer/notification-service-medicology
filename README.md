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

## Email template preview
- `GET /api/email-templates/notification/preview`
- `POST /api/email-templates/notification/preview`
- `GET /api/email-templates/streak-risk/preview`
- `POST /api/email-templates/streak-risk/preview`
- `GET /api/email-templates/everyday-reminder/preview`
- `POST /api/email-templates/everyday-reminder/preview`
- `GET /api/email-templates/calling-back/preview`
- `POST /api/email-templates/calling-back/preview`

The POST endpoint accepts `recipientName`, `headline`, `message`, `actionText`, `actionUrl`,
`secondaryMessage`, and `supportEmail`, then returns the rendered HTML email. The notification
everyday reminder, and calling-back templates use this shape.

The streak-risk POST endpoint accepts `recipientName`, `currentStreak`, `actionText`,
`actionUrl`, and `supportEmail`, then returns the rendered HTML email.

## Send email
- `POST /api/emails/send`

The send endpoint accepts `toEmail`, optional `templateType`, copy fields, `currentStreak`,
and `lastActivityDate`. If `templateType` is omitted, the service chooses:
- `STREAK_RISK` when `currentStreak > 0` and `lastActivityDate` was yesterday.
- `CALLING_BACK` when `lastActivityDate` is missing or older than yesterday.
- `EVERYDAY_REMINDER` when the user was active today or still has a normal active streak.

Set `SENDGRID_FROM_EMAIL` and `SENDGRID_API_KEY` before sending real email.

## Build and test
- Windows: `./mvnw.cmd clean test`
- macOS/Linux: `./mvnw clean test`
