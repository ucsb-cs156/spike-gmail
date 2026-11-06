package edu.ucsb.cs156.spikegmail.services;

import edu.ucsb.cs156.spikegmail.entities.GmailSender;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Talks to Google on behalf of an admin that has authorized this application: sends mail through
 * the Gmail API, and looks up which Google account an access token belongs to.
 */
@Slf4j
@Service
public class GmailService {

  /** The name of the OAuth client registration (see application.properties) used for Gmail. */
  public static final String REGISTRATION_ID = "gmail";

  static final String SEND_URL = "https://gmail.googleapis.com/gmail/v1/users/me/messages/send";
  static final String USERINFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo";

  private final OAuth2AuthorizedClientManager authorizedClientManager;
  private final RestClient restClient;

  @Autowired
  public GmailService(
      OAuth2AuthorizedClientManager authorizedClientManager, RestClient.Builder restClientBuilder) {
    this(authorizedClientManager, restClientBuilder.build());
  }

  GmailService(OAuth2AuthorizedClientManager authorizedClientManager, RestClient restClient) {
    this.authorizedClientManager = authorizedClientManager;
    this.restClient = restClient;
  }

  /**
   * Send a plain text email, from the Google account of the given sender, using the Gmail API.
   *
   * @param sender the admin whose stored Gmail authorization is used
   * @param to recipient email address
   * @param subject subject line
   * @param body plain text body
   * @throws MessagingException if the message cannot be built
   * @throws IOException if the message cannot be serialized
   */
  public void sendEmail(GmailSender sender, String to, String subject, String body)
      throws MessagingException, IOException {
    String accessToken = getAccessToken(sender);
    String raw = buildRawMessage(sender.getEmail(), to, subject, body);

    restClient
        .post()
        .uri(SEND_URL)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("raw", raw))
        .retrieve()
        .toBodilessEntity();
    log.info("sent email from {} to {}", sender.getEmail(), to);
  }

  /**
   * Ask Google which account an access token belongs to. Requires the <code>email</code> scope.
   *
   * @param accessToken a Google access token
   * @return the email address of the account, or null if Google did not say
   */
  public String lookupEmail(String accessToken) {
    Map<String, Object> userInfo =
        restClient
            .get()
            .uri(USERINFO_URL)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .retrieve()
            .body(new ParameterizedTypeReference<Map<String, Object>>() {});
    return userInfo == null ? null : (String) userInfo.get("email");
  }

  /**
   * Get a valid access token for the sender. The authorized client manager loads the stored token
   * and, if it has expired, uses the stored refresh token to get a new one (and stores that).
   */
  String getAccessToken(GmailSender sender) {
    OAuth2AuthorizeRequest request =
        OAuth2AuthorizeRequest.withClientRegistrationId(REGISTRATION_ID)
            .principal(sender.getPrincipalName())
            .build();
    OAuth2AuthorizedClient client = authorizedClientManager.authorize(request);
    if (client == null) {
      throw new IllegalStateException(
          "No Gmail authorization found for " + sender.getEmail() + "; they must authorize again");
    }
    return client.getAccessToken().getTokenValue();
  }

  /**
   * Build the value of the <code>raw</code> field of the Gmail API's messages.send method: an RFC
   * 2822 message, base64url encoded.
   */
  static String buildRawMessage(String from, String to, String subject, String body)
      throws MessagingException, IOException {
    MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
    message.setFrom(new InternetAddress(from));
    message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
    message.setSubject(subject, "UTF-8");
    message.setText(body, "UTF-8");
    message.setSentDate(new Date());

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    message.writeTo(out);
    return Base64.getUrlEncoder().encodeToString(out.toByteArray());
  }
}
