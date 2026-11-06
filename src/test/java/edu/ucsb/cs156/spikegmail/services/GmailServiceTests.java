package edu.ucsb.cs156.spikegmail.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import edu.ucsb.cs156.spikegmail.entities.GmailSender;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.Base64;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

public class GmailServiceTests {

  private OAuth2AuthorizedClientManager manager;
  private MockRestServiceServer server;
  private GmailService service;
  private GmailSender sender;

  @BeforeEach
  public void setUp() {
    manager = mock(OAuth2AuthorizedClientManager.class);
    RestClient.Builder builder = RestClient.builder();
    server = MockRestServiceServer.bindTo(builder).build();
    service = new GmailService(manager, builder.build());
    sender =
        GmailSender.builder().id(7L).email("admin@ucsb.edu").principalName("sub-123").build();
  }

  private OAuth2AuthorizedClient clientWithToken(String tokenValue) {
    ClientRegistration registration =
        ClientRegistration.withRegistrationId("gmail")
            .clientId("id")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("http://localhost/authorized/gmail")
            .authorizationUri("https://example.org/auth")
            .tokenUri("https://example.org/token")
            .build();
    OAuth2AccessToken token =
        new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            tokenValue,
            Instant.now(),
            Instant.now().plusSeconds(3600));
    return new OAuth2AuthorizedClient(registration, "sub-123", token);
  }

  @Test
  public void buildRawMessage_is_a_base64url_encoded_mime_message() throws Exception {
    String raw = GmailService.buildRawMessage("admin@ucsb.edu", "user@ucsb.edu", "Héllo", "Body text");

    byte[] bytes = Base64.getUrlDecoder().decode(raw);
    MimeMessage parsed =
        new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(bytes));

    assertEquals("admin@ucsb.edu", parsed.getFrom()[0].toString());
    assertEquals("user@ucsb.edu", parsed.getRecipients(Message.RecipientType.TO)[0].toString());
    assertEquals("Héllo", parsed.getSubject());
    assertEquals("Body text", parsed.getContent().toString().trim());
  }

  @Test
  public void getAccessToken_asks_the_manager_for_the_gmail_client_of_the_senders_principal() {
    when(manager.authorize(any())).thenReturn(clientWithToken("abc"));

    String token = service.getAccessToken(sender);

    assertEquals("abc", token);
    ArgumentCaptor<OAuth2AuthorizeRequest> captor =
        ArgumentCaptor.forClass(OAuth2AuthorizeRequest.class);
    verify(manager).authorize(captor.capture());
    assertEquals("gmail", captor.getValue().getClientRegistrationId());
    assertEquals("sub-123", captor.getValue().getPrincipal().getName());
  }

  @Test
  public void getAccessToken_throws_if_there_is_no_authorization() {
    when(manager.authorize(any())).thenReturn(null);

    assertThrows(IllegalStateException.class, () -> service.getAccessToken(sender));
  }

  @Test
  public void sendEmail_posts_the_message_to_gmail_with_a_bearer_token() throws Exception {
    when(manager.authorize(any())).thenReturn(clientWithToken("abc"));
    server
        .expect(requestTo(GmailService.SEND_URL))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Bearer abc"))
        .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
        .andExpect(
            org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath("$.raw")
                .isNotEmpty())
        .andRespond(withSuccess("{\"id\":\"1\"}", MediaType.APPLICATION_JSON));

    service.sendEmail(sender, "user@ucsb.edu", "Subject", "Body");

    server.verify();
  }

  @Test
  public void lookupEmail_returns_the_email_google_reports() {
    server
        .expect(requestTo(GmailService.USERINFO_URL))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header("Authorization", "Bearer abc"))
        .andRespond(
            withSuccess("{\"sub\":\"1\",\"email\":\"admin@ucsb.edu\"}", MediaType.APPLICATION_JSON));

    assertEquals("admin@ucsb.edu", service.lookupEmail("abc"));
    server.verify();
  }

  @Test
  public void lookupEmail_returns_null_when_google_gives_no_body() {
    server
        .expect(requestTo(GmailService.USERINFO_URL))
        .andRespond(withSuccess("null", MediaType.APPLICATION_JSON));

    assertNull(service.lookupEmail("abc"));
  }
}
