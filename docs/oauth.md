# Google OAuth setup for spike-gmail

This app uses **one Google OAuth client** (one `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` pair) for **two different purposes**:

| | Registration id | Who uses it | Scopes requested | Redirect URI |
|---|---|---|---|---|
| **Login** | `google` | Everyone | `email`, `profile` | `{baseUrl}/login/oauth2/code/google` |
| **Send Gmail** | `gmail` | Only an admin who clicks "Authorize this app to send Gmail on my behalf" | `email`, `https://www.googleapis.com/auth/gmail.send` | `{baseUrl}/authorized/gmail` |

Both registrations are in `src/main/resources/application.properties` and read the same two environment variables.

Regular users are never shown the Gmail permission: it is requested in a separate OAuth round trip that only admins can start. That round trip does not log anybody in; it just stores a Gmail access token and refresh token for the admin that started it.

Setting this up on localhost takes steps 1–5 below. Getting it to work on Dokku also needs step 6.

> The Google Cloud Console is redesigned from time to time. The names below ("Google Auth Platform", "Branding", "Audience", "Data Access", "Clients") are what it was called when this was written; if a menu has moved, look for the same words.

## Step 1: Create (or choose) a Google Cloud project

At <https://console.cloud.google.com/>, create a project (or reuse your course project). If this is your first time, the course guides cover steps 1 and 2:

* Create a project: <https://ucsb-cs156.github.io/topics/oauth/google_create_developer_project.html>
* Consent screen: <https://ucsb-cs156.github.io/topics/oauth/google_oauth_consent_screen.html>

## Step 2: Enable the Gmail API

The scope alone is not enough; the project must also have the API turned on.

1. **APIs & Services → Library**
2. Search for **Gmail API**
3. Click **Enable**

## Step 3: Configure the consent screen (Google Auth Platform)

Go to **Google Auth Platform** (or **APIs & Services → OAuth consent screen**).

**Branding**: fill in the app name, the user support email, and the developer contact email.

**Audience**: choose **Internal**. **Any application that uses the `gmail.send` API should use the Internal user type.**

* **Internal** means only accounts in your Google Workspace organization (for UCSB, the `ucsb.edu` accounts) can log in. In exchange:
  * no Google app verification is needed, even for sensitive scopes like `gmail.send`;
  * there is no "Test users" list to maintain;
  * there is no "Google hasn't verified this app" warning;
  * **refresh tokens do not expire after 7 days**, so the admin authorizes once and the app keeps sending mail.
* **Why not External?** An External app has to choose between two bad options for an app that sends Gmail:
  * **Testing** status: only listed test users can log in (up to 100), users see an "unverified app" warning, and **refresh tokens expire after 7 days** for any app requesting non-basic scopes. An admin would have to click "Re-authorize" about once a week, and the app silently stops sending mail when they forget.
  * **In production** status: because `gmail.send` is a *sensitive scope*, Google requires an app verification review before the restrictions go away.
* **The consequence of Internal:** every user, admins included, must log in with an account in your organization. A personal `@gmail.com` account will be rejected by Google. That's the intended behavior for these course apps.
* **If Internal is greyed out or missing:** the Google Cloud project is not inside your Workspace organization. Create the project under your organization (for UCSB, while signed in with your `ucsb.edu` account, check that the project's organization is shown as UCSB), or ask your Workspace admin. The user type can also be changed later under **Audience**, but it is simplest to choose Internal from the start.

## Step 4: Configure the scopes (Data Access)

Go to **Google Auth Platform → Data Access** (or, in the older UI, the *Scopes* step of the consent screen) and click **Add or remove scopes**. Add all of these:

| Scope | Why |
|---|---|
| `openid` | Standard sign-in scope |
| `.../auth/userinfo.email` (shown as "See your primary Google Account email address") | Login, and to verify which account an admin authorized |
| `.../auth/userinfo.profile` (shown as "See your personal info...") | Login |
| `https://www.googleapis.com/auth/gmail.send` (shown as "Send email on your behalf") | **Only** requested by the `gmail` registration. Needed to send mail. |

If `gmail.send` doesn't appear in the list, either the Gmail API isn't enabled yet (Step 2) or you can paste it into the **Manually add scopes** box at the bottom of the dialog. Then click **Update** and **Save**.

Notes:

* `gmail.send` is the narrowest Gmail scope that can send mail: it **cannot read** the admin's mailbox.
* Adding a scope to the consent screen only declares what the app is *allowed* to ask for. Whether a particular login asks for it is controlled by the `scope` lines in `application.properties`. That is why regular users, who log in through the `google` registration, never see the Gmail permission.

## Step 5: Create the OAuth client and set the environment variables

1. Go to **Google Auth Platform → Clients** (or **APIs & Services → Credentials → Create credentials → OAuth client ID**).
2. **Application type:** *Web application*.
3. **Name:** anything, e.g. `spike-gmail`.
4. **Authorized JavaScript origins:** leave empty (this app does not use Google's browser libraries).
5. **Authorized redirect URIs:** add **both** of these for every place the app runs.

   For localhost:

   ```
   http://localhost:8080/login/oauth2/code/google
   http://localhost:8080/authorized/gmail
   ```

   For a Dokku deployment (use your real hostname):

   ```
   https://spike-gmail.dokku-00.cs.ucsb.edu/login/oauth2/code/google
   https://spike-gmail.dokku-00.cs.ucsb.edu/authorized/gmail
   ```

   The first URI is used by login; the second by the Gmail authorization. Google compares them character for character, so no trailing slashes, and `http` vs `https` matters.
6. Click **Create**. Copy the **Client ID** and **Client secret**. (The secret can be viewed again later on the client's page.)

Now create your `.env` file:

```
cp .env.SAMPLE .env
```

and edit it:

```
GOOGLE_CLIENT_ID=1234567890-abcdefg.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=GOCSPX-xxxxxxxxxxxxxxxxxxxx
TOKEN_ENCRYPTION_KEY=<output of: openssl rand -base64 32>
ADMIN_EMAILS=phtcon@ucsb.edu
```

* `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` are the two values from step 5. There is only *one* of each; both the login registration and the Gmail registration use the same pair.
* `TOKEN_ENCRYPTION_KEY` is the key used to encrypt the Google access and refresh tokens before they are stored in the database (see "Token encryption" below). Generate it with `openssl rand -base64 32`. **The app cannot log anyone in until it is set to a valid key**, because Spring stores a token for every login and refuses to store it unencrypted.
* `ADMIN_EMAILS` is a comma-separated list of the Google accounts that are admins (**no spaces**). Only these users see the "Authorize ... Gmail" link, and only they can use `/oauth2/authorization/gmail`.
* `.env` contains secrets and is in `.gitignore`. Never commit it.

## Step 6 (Dokku only): set the same values on the server

```
dokku config:set --no-restart spike-gmail GOOGLE_CLIENT_ID=... GOOGLE_CLIENT_SECRET=... TOKEN_ENCRYPTION_KEY=$(openssl rand -base64 32) ADMIN_EMAILS=... PRODUCTION=true
```

Also give the app a Postgres database (see <https://ucsb-cs156.github.io/topics/dokku/postgres_database.html>). The tokens are stored in the database, so a redeploy does not make the admin authorize again.

## Using the app

1. Start it with `mvn spring-boot:run` and open <http://localhost:8080>.
2. Log in with Google. You'll see only the basic "email and profile" permissions.
3. As an admin, click **Authorize this app to send Gmail on my behalf**. This is when Google shows the "Send email on your behalf" permission.
4. After you approve, you're returned to the home page, which now lists you as a sender.
5. Any logged in user can then click **Send me an email from *admin@...***, which sends a test message to *their own* address, from that admin's Gmail account.

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| `Error 400: redirect_uri_mismatch` | The redirect URI in Step 5 isn't listed exactly. Read the URI in the error page; it is the one the app sent. A login failure points at `/login/oauth2/code/google`, an authorization failure at `/authorized/gmail`. On Dokku, also make sure the app is using `https` (see `server.forward-headers-strategy` in `application.properties`). |
| `Error 401: invalid_client` | `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` not set (the defaults are `client_id_unset` / `client_secret_unset`). |
| `Error 403: org_internal` | The app is **Internal** and the account used to log in is not in your Workspace organization (for example a personal `@gmail.com` account). Log in with an organization account. |
| `Error 403: access_denied` or "app not verified / has not completed the verification process" | The app's user type is **External**. Switch it to **Internal** (Step 3). If it must stay External, the account must be listed under **Test users**. |
| "Google hasn't verified this app" warning | Only appears for External apps. Switch to **Internal** (Step 3). |
| Admin approves Gmail but the page says "authorized a different Google account" | On Google's account chooser the admin picked an account other than the one they're logged in with. Try again and choose the same account. |
| "Google did not issue a refresh token" | The app always asks for one (`access_type=offline`, `prompt=consent`). If you see this, check that you did not remove those parameters in `GmailAuthorizationRequestResolver`. |
| Sending worked, then fails with `invalid_grant` (a few days later) | The refresh token expired or was revoked. If the app is External and in Testing, it expires after 7 days: switch the app to **Internal** (Step 3). Otherwise the admin can click **Re-authorize**. They can also revoke access at <https://myaccount.google.com/permissions>. |
| Sending fails with a 403 from `gmail.googleapis.com` | The Gmail API isn't enabled for the project (Step 2), or the admin authorized without `gmail.send` (Step 4). |
| Login fails with "Token encryption key is not configured" (or "must be Base64 encoded" / "must decode to 16, 24, or 32 bytes") | `TOKEN_ENCRYPTION_KEY` is missing or malformed. Generate one with `openssl rand -base64 32`. |
| Sending fails with "Failed to decrypt token" | `TOKEN_ENCRYPTION_KEY` changed after the admin authorized (or the database came from another deployment). The admin must click **Re-authorize**. |
| Clicking the Gmail link as a non-admin gives a 404 | Working as intended. |

## Token encryption

The access token and refresh token Google issues are what allow this app to send mail as the admin, so they are **encrypted before they are written to the database**. The `oauth2_authorized_client` table only ever contains ciphertext.

This follows the pattern used for Canvas and Slack tokens in [proj-frontiers](https://github.com/ucsb-cs156/proj-frontiers) (`CanvasApiTokenSecurityService`):

* **AES-GCM** (`AES/GCM/NoPadding`), a random 96-bit IV per value, a 128-bit authentication tag (tampered ciphertext fails to decrypt).
* Stored as `enc:v1:<base64(iv + ciphertext)>`.
* The key is the Base64 value of `TOKEN_ENCRYPTION_KEY` (16, 24 or 32 bytes; 32 recommended), mapped to `app.token-encryption-key`. It is never in source control.
* **Fails closed:** if the key is missing or malformed, saving a token throws and nothing is stored.
* Values that do not start with `enc:v1:` are read as plaintext, for rows written before encryption existed.

In code: `TokenSecurityService` does the encrypting, and `EncryptingOAuth2AuthorizedClientService` wraps Spring Security's `JdbcOAuth2AuthorizedClientService` so tokens are encrypted on save and decrypted on load. Nothing else in the app knows about it.

Keep the **same key for the life of a database**. If it changes, tokens that were already stored can no longer be decrypted, and each admin has to click **Re-authorize**.

## Security notes for a spike

* Anyone who has both the database **and** `TOKEN_ENCRYPTION_KEY` can read the tokens. Keep the key out of the database server's reach (a Dokku config var is a separate store from the Postgres service, but not a vault).
* A refresh token stays valid until the admin revokes it at <https://myaccount.google.com/permissions> or it is replaced, so treat the key and the database as sensitive.
* The "send test email" endpoint only ever sends to the logged-in user's own address, so it cannot be used to spam other people. There is no rate limit; add one before letting it send anything user-defined.
* Google will rewrite the `From` header to the authorizing account anyway; the app sets it to the same address so the two agree.
