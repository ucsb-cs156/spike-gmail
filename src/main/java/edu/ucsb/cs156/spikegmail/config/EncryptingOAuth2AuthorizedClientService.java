package edu.ucsb.cs156.spikegmail.config;

import edu.ucsb.cs156.spikegmail.services.TokenSecurityService;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;

/**
 * Wraps another {@link OAuth2AuthorizedClientService} (here, the JDBC one) so that access tokens
 * and refresh tokens are encrypted before they reach it, and decrypted when they come back.
 *
 * <p>Everything outside this class sees ordinary plaintext tokens. Everything inside the database
 * is ciphertext, in the form produced by {@link TokenSecurityService}. Only the token values are
 * encrypted; the token type, issue and expiry times and scopes are stored as usual.
 *
 * <p>If the encryption key is missing, saving a token fails (it is never stored in plaintext). If
 * the key has changed since a token was stored, loading it fails with an {@link
 * IllegalStateException}, and the admin must authorize again.
 */
public class EncryptingOAuth2AuthorizedClientService implements OAuth2AuthorizedClientService {

  private final OAuth2AuthorizedClientService delegate;
  private final TokenSecurityService tokenSecurityService;

  public EncryptingOAuth2AuthorizedClientService(
      OAuth2AuthorizedClientService delegate, TokenSecurityService tokenSecurityService) {
    this.delegate = delegate;
    this.tokenSecurityService = tokenSecurityService;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(
      String clientRegistrationId, String principalName) {
    OAuth2AuthorizedClient stored = delegate.loadAuthorizedClient(clientRegistrationId, principalName);
    if (stored == null) {
      return null;
    }
    return (T) transform(stored, false);
  }

  @Override
  public void saveAuthorizedClient(OAuth2AuthorizedClient authorizedClient, Authentication principal) {
    delegate.saveAuthorizedClient(transform(authorizedClient, true), principal);
  }

  @Override
  public void removeAuthorizedClient(String clientRegistrationId, String principalName) {
    delegate.removeAuthorizedClient(clientRegistrationId, principalName);
  }

  private OAuth2AuthorizedClient transform(OAuth2AuthorizedClient client, boolean encrypt) {
    OAuth2AccessToken accessToken = client.getAccessToken();
    OAuth2AccessToken newAccessToken =
        new OAuth2AccessToken(
            accessToken.getTokenType(),
            convert(accessToken.getTokenValue(), encrypt),
            accessToken.getIssuedAt(),
            accessToken.getExpiresAt(),
            accessToken.getScopes());

    OAuth2RefreshToken refreshToken = client.getRefreshToken();
    OAuth2RefreshToken newRefreshToken =
        refreshToken == null
            ? null
            : new OAuth2RefreshToken(
                convert(refreshToken.getTokenValue(), encrypt), refreshToken.getIssuedAt());

    return new OAuth2AuthorizedClient(
        client.getClientRegistration(), client.getPrincipalName(), newAccessToken, newRefreshToken);
  }

  private String convert(String value, boolean encrypt) {
    return encrypt ? tokenSecurityService.encrypt(value) : tokenSecurityService.decrypt(value);
  }
}
