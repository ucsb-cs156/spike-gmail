# spike-gmail

A spike showing how a Spring Boot app can **send email through the Gmail API on behalf of one admin who has authorized it**, without asking *regular* users to approve anything about Gmail.

Useful when an app needs to send email, but you can't create a Google service account, so mail has to come from a real person's account.

## What the app does

A deliberately minimal Thymeleaf UI:

1. **Not logged in:** the home page shows only a *Log in with Google* link.
2. **Admins** are defined by the `ADMIN_EMAILS` environment variable (as in `STARTER-team01`).
3. **Logged in as an admin:** a link to *Authorize this app to send Gmail on my behalf*.
4. **Logged in as anyone:** one button per admin who has authorized. Clicking it sends a test email **to the logged in user**, **from** that admin.

## How it works (the short version)

Spring Security's `oauth2Login()` and `oauth2Client()` are used together, with two registrations that share one Google OAuth client but ask for different scopes:

* **`google`** (login): scopes `email, profile`. Used by everyone.
* **`gmail`** (authorization only): scopes `email, gmail.send`, redirect URI `/authorized/gmail`. Only an admin can start it; `GmailAuthorizationRequestResolver` refuses everybody else. It adds `access_type=offline` and `prompt=consent` so Google issues a **refresh token**.

The resulting tokens are stored by `JdbcOAuth2AuthorizedClientService`, in the database, keyed by `gmail` + the admin's Google id. That is what lets *other* users trigger a send later: `GmailService` asks an `OAuth2AuthorizedClientManager` for the admin's token (refreshing it if it expired) and `POST`s to `gmail.googleapis.com/gmail/v1/users/me/messages/send`.

| Class | Role |
|---|---|
| `config/SecurityConfig` | Login + the second OAuth client flow; `ROLE_ADMIN` from `ADMIN_EMAILS` |
| `config/OAuth2ClientConfig` | JDBC token storage, token manager (with refresh), resolver bean |
| `config/GmailAuthorizationRequestResolver` | Admin-only guard; adds the offline-access parameters |
| `controllers/GmailAuthorizationController` | Landing point after consent; verifies the admin authorized *their own* account; records the sender |
| `controllers/GmailSendController` | Sends the test email to the logged in user |
| `services/GmailService` | Builds the MIME message, calls the Gmail API |

## Setup

**Before running, follow [docs/oauth.md](docs/oauth.md).** It covers creating the Google OAuth client, the two `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` values, and which scopes to configure in the Google Cloud Console (including the `gmail.send` scope and the Gmail API).

```
cp .env.SAMPLE .env     # then edit .env
mvn spring-boot:run
```

Then open <http://localhost:8080>.

Java 25 and Maven, as in the course starter (`.sdkmanrc`, `./mvnw`).

## Tests

```
mvn test
```

These are plain unit tests (no Google calls, no Spring context). The real OAuth round trip with Google can only be checked by hand, following the steps in [docs/oauth.md](docs/oauth.md).

## Differences from the starter

This is a spike, so it leaves out the starter's JaCoCo 100% gate, Pitest, the Google-Java-Format check, Swagger, and the React/REST pieces. It is otherwise structured like `STARTER-team01`: same package layout, Liquibase JSON changelogs, `.env` / `spring-dotenv` configuration, `ADMIN_EMAILS`, Dockerfile and Dokku conventions.
