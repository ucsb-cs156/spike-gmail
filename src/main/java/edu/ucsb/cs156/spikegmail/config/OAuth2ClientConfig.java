package edu.ucsb.cs156.spikegmail.config;

import edu.ucsb.cs156.spikegmail.services.TokenSecurityService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.JdbcOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;

/** Beans that let the app keep, and later use, the Gmail tokens an admin has authorized. */
@Configuration
public class OAuth2ClientConfig {

  /**
   * Store authorized clients (the access and refresh tokens) in the database instead of in memory.
   * Spring Boot would otherwise create an in-memory service, and every restart or redeploy would
   * silently forget that the admin had authorized Gmail. The table is created by Liquibase (see
   * db/migration/changes/OAuth2AuthorizedClient.json).
   *
   * <p>The tokens are encrypted (AES-GCM, key in TOKEN_ENCRYPTION_KEY) before they reach the
   * database, and decrypted when they are read back.
   *
   * @param jdbcOperations the Spring JdbcTemplate
   * @param clientRegistrationRepository the registrations from application.properties
   * @param tokenSecurityService encrypts and decrypts token values
   * @return the encrypting, JDBC-backed service
   */
  @Bean
  public OAuth2AuthorizedClientService authorizedClientService(
      JdbcOperations jdbcOperations,
      ClientRegistrationRepository clientRegistrationRepository,
      TokenSecurityService tokenSecurityService) {
    return new EncryptingOAuth2AuthorizedClientService(
        new JdbcOAuth2AuthorizedClientService(jdbcOperations, clientRegistrationRepository),
        tokenSecurityService);
  }

  /**
   * A manager that works outside of a web request (the person whose Gmail we use is typically not
   * the person who is logged in). It loads the stored token for a named principal and, if the
   * access token has expired, silently exchanges the stored refresh token for a new one.
   *
   * @param clientRegistrationRepository the registrations from application.properties
   * @param authorizedClientService where the tokens are stored
   * @return the manager
   */
  @Bean
  public OAuth2AuthorizedClientManager authorizedClientManager(
      ClientRegistrationRepository clientRegistrationRepository,
      OAuth2AuthorizedClientService authorizedClientService) {
    OAuth2AuthorizedClientProvider provider =
        OAuth2AuthorizedClientProviderBuilder.builder().authorizationCode().refreshToken().build();
    AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
        new AuthorizedClientServiceOAuth2AuthorizedClientManager(
            clientRegistrationRepository, authorizedClientService);
    manager.setAuthorizedClientProvider(provider);
    return manager;
  }

  /**
   * The resolver that adds the offline-access parameters (and the admin-only check) to the
   * redirect to Google for the gmail registration.
   *
   * @param clientRegistrationRepository the registrations from application.properties
   * @return the resolver
   */
  @Bean
  public OAuth2AuthorizationRequestResolver authorizationRequestResolver(
      ClientRegistrationRepository clientRegistrationRepository) {
    return new GmailAuthorizationRequestResolver(clientRegistrationRepository);
  }
}
