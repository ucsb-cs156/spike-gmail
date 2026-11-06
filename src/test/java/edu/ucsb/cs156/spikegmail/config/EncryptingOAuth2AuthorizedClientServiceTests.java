package edu.ucsb.cs156.spikegmail.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.ucsb.cs156.spikegmail.services.TokenSecurityService;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;

public class EncryptingOAuth2AuthorizedClientServiceTests {

  private static final String KEY =
      Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());

  private OAuth2AuthorizedClientService delegate;
  private TokenSecurityService tokenSecurityService;
  private EncryptingOAuth2AuthorizedClientService service;
  private ClientRegistration registration;
  private Authentication principal;
  private Instant issuedAt;
  private Instant expiresAt;

  @BeforeEach
  public void setUp() {
    delegate = mock(OAuth2AuthorizedClientService.class);
    tokenSecurityService = new TokenSecurityService(KEY);
    service = new EncryptingOAuth2AuthorizedClientService(delegate, tokenSecurityService);
    registration =
        ClientRegistration.withRegistrationId("gmail")
            .clientId("id")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("http://localhost/authorized/gmail")
            .authorizationUri("https://example.org/auth")
            .tokenUri("https://example.org/token")
            .build();
    principal = new TestingAuthenticationToken("sub-123", "pw");
    issuedAt = Instant.parse("2026-01-01T00:00:00Z");
    expiresAt = issuedAt.plusSeconds(3600);
  }

  private OAuth2AuthorizedClient client(String access, String refresh) {
    OAuth2AccessToken accessToken =
        new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER, access, issuedAt, expiresAt, Set.of("a", "b"));
    OAuth2RefreshToken refreshToken =
        refresh == null ? null : new OAuth2RefreshToken(refresh, issuedAt);
    return new OAuth2AuthorizedClient(registration, "sub-123", accessToken, refreshToken);
  }

  @Test
  public void save_encrypts_both_tokens_before_delegating() {
    service.saveAuthorizedClient(client("access-secret", "refresh-secret"), principal);

    ArgumentCaptor<OAuth2AuthorizedClient> captor =
        ArgumentCaptor.forClass(OAuth2AuthorizedClient.class);
    verify(delegate).saveAuthorizedClient(captor.capture(), any());
    OAuth2AuthorizedClient saved = captor.getValue();

    String storedAccess = saved.getAccessToken().getTokenValue();
    String storedRefresh = saved.getRefreshToken().getTokenValue();
    assertTrue(storedAccess.startsWith("enc:v1:"));
    assertTrue(storedRefresh.startsWith("enc:v1:"));
    assertEquals("access-secret", tokenSecurityService.decrypt(storedAccess));
    assertEquals("refresh-secret", tokenSecurityService.decrypt(storedRefresh));
  }

  @Test
  public void save_preserves_everything_except_the_token_values() {
    service.saveAuthorizedClient(client("access-secret", "refresh-secret"), principal);

    ArgumentCaptor<OAuth2AuthorizedClient> captor =
        ArgumentCaptor.forClass(OAuth2AuthorizedClient.class);
    verify(delegate).saveAuthorizedClient(captor.capture(), any());
    OAuth2AuthorizedClient saved = captor.getValue();

    assertSame(registration, saved.getClientRegistration());
    assertEquals("sub-123", saved.getPrincipalName());
    assertEquals(OAuth2AccessToken.TokenType.BEARER, saved.getAccessToken().getTokenType());
    assertEquals(issuedAt, saved.getAccessToken().getIssuedAt());
    assertEquals(expiresAt, saved.getAccessToken().getExpiresAt());
    assertEquals(Set.of("a", "b"), saved.getAccessToken().getScopes());
    assertEquals(issuedAt, saved.getRefreshToken().getIssuedAt());
  }

  @Test
  public void save_passes_the_principal_through() {
    service.saveAuthorizedClient(client("access-secret", null), principal);

    verify(delegate).saveAuthorizedClient(any(), org.mockito.ArgumentMatchers.eq(principal));
  }

  @Test
  public void save_without_a_refresh_token_stores_none() {
    service.saveAuthorizedClient(client("access-secret", null), principal);

    ArgumentCaptor<OAuth2AuthorizedClient> captor =
        ArgumentCaptor.forClass(OAuth2AuthorizedClient.class);
    verify(delegate).saveAuthorizedClient(captor.capture(), any());
    assertNull(captor.getValue().getRefreshToken());
  }

  @Test
  public void save_without_an_encryption_key_fails_and_stores_nothing() {
    EncryptingOAuth2AuthorizedClientService noKey =
        new EncryptingOAuth2AuthorizedClientService(delegate, new TokenSecurityService(""));

    assertThrows(
        IllegalStateException.class,
        () -> noKey.saveAuthorizedClient(client("access-secret", "refresh-secret"), principal));

    verify(delegate, never()).saveAuthorizedClient(any(), any());
  }

  @Test
  public void load_decrypts_both_tokens() {
    String encAccess = tokenSecurityService.encrypt("access-secret");
    String encRefresh = tokenSecurityService.encrypt("refresh-secret");
    when(delegate.loadAuthorizedClient("gmail", "sub-123"))
        .thenReturn(client(encAccess, encRefresh));

    OAuth2AuthorizedClient loaded = service.loadAuthorizedClient("gmail", "sub-123");

    assertEquals("access-secret", loaded.getAccessToken().getTokenValue());
    assertEquals("refresh-secret", loaded.getRefreshToken().getTokenValue());
    assertSame(registration, loaded.getClientRegistration());
    assertEquals("sub-123", loaded.getPrincipalName());
    assertEquals(expiresAt, loaded.getAccessToken().getExpiresAt());
    assertEquals(Set.of("a", "b"), loaded.getAccessToken().getScopes());
  }

  @Test
  public void load_without_a_refresh_token() {
    when(delegate.loadAuthorizedClient("gmail", "sub-123"))
        .thenReturn(client(tokenSecurityService.encrypt("access-secret"), null));

    OAuth2AuthorizedClient loaded = service.loadAuthorizedClient("gmail", "sub-123");

    assertEquals("access-secret", loaded.getAccessToken().getTokenValue());
    assertNull(loaded.getRefreshToken());
  }

  @Test
  public void load_returns_null_when_nothing_is_stored() {
    when(delegate.loadAuthorizedClient("gmail", "sub-123")).thenReturn(null);

    assertNull(service.loadAuthorizedClient("gmail", "sub-123"));
  }

  @Test
  public void load_still_reads_legacy_plaintext_tokens() {
    when(delegate.loadAuthorizedClient("gmail", "sub-123"))
        .thenReturn(client("plain-access", "plain-refresh"));

    OAuth2AuthorizedClient loaded = service.loadAuthorizedClient("gmail", "sub-123");

    assertEquals("plain-access", loaded.getAccessToken().getTokenValue());
    assertEquals("plain-refresh", loaded.getRefreshToken().getTokenValue());
  }

  @Test
  public void load_fails_when_tokens_were_encrypted_with_another_key() {
    String encAccess =
        new TokenSecurityService(
                Base64.getEncoder().encodeToString("fedcba9876543210fedcba9876543210".getBytes()))
            .encrypt("access-secret");
    when(delegate.loadAuthorizedClient("gmail", "sub-123")).thenReturn(client(encAccess, null));

    assertThrows(
        IllegalStateException.class, () -> service.loadAuthorizedClient("gmail", "sub-123"));
  }

  @Test
  public void what_is_saved_can_be_loaded_back() {
    ArgumentCaptor<OAuth2AuthorizedClient> captor =
        ArgumentCaptor.forClass(OAuth2AuthorizedClient.class);
    service.saveAuthorizedClient(client("access-secret", "refresh-secret"), principal);
    verify(delegate).saveAuthorizedClient(captor.capture(), any());
    OAuth2AuthorizedClient stored = captor.getValue();
    assertNotEquals("access-secret", stored.getAccessToken().getTokenValue());
    when(delegate.loadAuthorizedClient("gmail", "sub-123")).thenReturn(stored);

    OAuth2AuthorizedClient loaded = service.loadAuthorizedClient("gmail", "sub-123");

    assertEquals("access-secret", loaded.getAccessToken().getTokenValue());
    assertEquals("refresh-secret", loaded.getRefreshToken().getTokenValue());
  }

  @Test
  public void remove_is_delegated() {
    service.removeAuthorizedClient("gmail", "sub-123");

    verify(delegate).removeAuthorizedClient("gmail", "sub-123");
  }
}
