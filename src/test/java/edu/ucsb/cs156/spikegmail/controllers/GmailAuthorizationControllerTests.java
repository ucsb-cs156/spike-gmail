package edu.ucsb.cs156.spikegmail.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.ucsb.cs156.spikegmail.entities.GmailSender;
import edu.ucsb.cs156.spikegmail.entities.User;
import edu.ucsb.cs156.spikegmail.repositories.GmailSenderRepository;
import edu.ucsb.cs156.spikegmail.services.CurrentUserService;
import edu.ucsb.cs156.spikegmail.services.GmailService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

@ExtendWith(MockitoExtension.class)
public class GmailAuthorizationControllerTests {

  @Mock CurrentUserService currentUserService;
  @Mock OAuth2AuthorizedClientService authorizedClientService;
  @Mock GmailService gmailService;
  @Mock GmailSenderRepository gmailSenderRepository;

  GmailAuthorizationController controller;
  RedirectAttributes redirectAttributes;
  User admin;

  @BeforeEach
  public void setUp() {
    controller =
        new GmailAuthorizationController(
            currentUserService, authorizedClientService, gmailService, gmailSenderRepository);
    redirectAttributes = new RedirectAttributesModelMap();
    admin = User.builder().email("admin@ucsb.edu").admin(true).build();
    when(currentUserService.getUser()).thenReturn(admin);
    when(currentUserService.getPrincipalName()).thenReturn("sub-123");
  }

  private OAuth2AuthorizedClient client(boolean withRefreshToken) {
    ClientRegistration registration =
        ClientRegistration.withRegistrationId("gmail")
            .clientId("id")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("http://localhost/authorized/gmail")
            .authorizationUri("https://example.org/auth")
            .tokenUri("https://example.org/token")
            .build();
    OAuth2AccessToken access =
        new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            "access",
            Instant.now(),
            Instant.now().plusSeconds(3600));
    OAuth2RefreshToken refresh = withRefreshToken ? new OAuth2RefreshToken("refresh", Instant.now()) : null;
    return new OAuth2AuthorizedClient(registration, "sub-123", access, refresh);
  }

  @Test
  public void no_stored_client_is_an_error() {
    when(authorizedClientService.loadAuthorizedClient("gmail", "sub-123")).thenReturn(null);

    String view = controller.authorized(redirectAttributes);

    assertEquals("redirect:/", view);
    assertNotNull(redirectAttributes.getFlashAttributes().get("error"));
    verify(gmailSenderRepository, never()).save(any());
  }

  @Test
  public void missing_refresh_token_discards_the_authorization() {
    when(authorizedClientService.loadAuthorizedClient("gmail", "sub-123"))
        .thenReturn(client(false));

    String view = controller.authorized(redirectAttributes);

    assertEquals("redirect:/", view);
    assertNotNull(redirectAttributes.getFlashAttributes().get("error"));
    verify(authorizedClientService).removeAuthorizedClient("gmail", "sub-123");
    verify(gmailSenderRepository, never()).save(any());
  }

  @Test
  public void authorizing_a_different_google_account_discards_the_authorization() {
    when(authorizedClientService.loadAuthorizedClient("gmail", "sub-123"))
        .thenReturn(client(true));
    when(gmailService.lookupEmail("access")).thenReturn("someone.else@gmail.com");

    String view = controller.authorized(redirectAttributes);

    assertEquals("redirect:/", view);
    assertNotNull(redirectAttributes.getFlashAttributes().get("error"));
    verify(authorizedClientService).removeAuthorizedClient("gmail", "sub-123");
    verify(gmailSenderRepository, never()).save(any());
  }

  @Test
  public void google_not_reporting_an_email_discards_the_authorization() {
    when(authorizedClientService.loadAuthorizedClient("gmail", "sub-123"))
        .thenReturn(client(true));
    when(gmailService.lookupEmail("access")).thenReturn(null);

    controller.authorized(redirectAttributes);

    verify(authorizedClientService).removeAuthorizedClient("gmail", "sub-123");
    verify(gmailSenderRepository, never()).save(any());
  }

  @Test
  public void a_new_admin_is_recorded_as_a_sender() {
    when(authorizedClientService.loadAuthorizedClient("gmail", "sub-123"))
        .thenReturn(client(true));
    when(gmailService.lookupEmail("access")).thenReturn("ADMIN@ucsb.edu");
    when(gmailSenderRepository.findByEmail("admin@ucsb.edu")).thenReturn(Optional.empty());

    String view = controller.authorized(redirectAttributes);

    assertEquals("redirect:/", view);
    assertNotNull(redirectAttributes.getFlashAttributes().get("message"));
    ArgumentCaptor<GmailSender> captor = ArgumentCaptor.forClass(GmailSender.class);
    verify(gmailSenderRepository).save(captor.capture());
    assertEquals("admin@ucsb.edu", captor.getValue().getEmail());
    assertEquals("sub-123", captor.getValue().getPrincipalName());
    assertNotNull(captor.getValue().getAuthorizedAt());
    verify(authorizedClientService, never()).removeAuthorizedClient(any(), any());
  }

  @Test
  public void re_authorizing_updates_the_existing_sender() {
    GmailSender existing =
        GmailSender.builder().id(5L).email("admin@ucsb.edu").principalName("old-sub").build();
    when(authorizedClientService.loadAuthorizedClient("gmail", "sub-123"))
        .thenReturn(client(true));
    when(gmailService.lookupEmail("access")).thenReturn("admin@ucsb.edu");
    when(gmailSenderRepository.findByEmail("admin@ucsb.edu")).thenReturn(Optional.of(existing));

    controller.authorized(redirectAttributes);

    ArgumentCaptor<GmailSender> captor = ArgumentCaptor.forClass(GmailSender.class);
    verify(gmailSenderRepository).save(captor.capture());
    assertEquals(5L, captor.getValue().getId());
    assertEquals("sub-123", captor.getValue().getPrincipalName());
  }
}
