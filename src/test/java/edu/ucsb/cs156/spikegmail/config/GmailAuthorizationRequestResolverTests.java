package edu.ucsb.cs156.spikegmail.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

public class GmailAuthorizationRequestResolverTests {

  private OAuth2AuthorizationRequestResolver delegate;
  private GmailAuthorizationRequestResolver resolver;
  private HttpServletRequest request;

  @BeforeEach
  public void setUp() {
    delegate = mock(OAuth2AuthorizationRequestResolver.class);
    resolver = new GmailAuthorizationRequestResolver(delegate);
    request = mock(HttpServletRequest.class);
  }

  @AfterEach
  public void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private OAuth2AuthorizationRequest authorizationRequestFor(String registrationId) {
    return OAuth2AuthorizationRequest.authorizationCode()
        .authorizationUri("https://accounts.example.org/auth")
        .clientId("client")
        .redirectUri("http://localhost:8080/authorized/gmail")
        .scopes(Set.of("email"))
        .state("state")
        .attributes(attrs -> attrs.put(OAuth2ParameterNames.REGISTRATION_ID, registrationId))
        .build();
  }

  private void loginAsAdminWithEmail(String email) {
    DefaultOAuth2User principal =
        new DefaultOAuth2User(
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN")),
            Map.of("sub", "123", "email", email),
            "sub");
    SecurityContextHolder.getContext()
        .setAuthentication(
            new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google"));
  }

  @Test
  public void null_from_the_delegate_stays_null() {
    when(delegate.resolve(request)).thenReturn(null);

    assertNull(resolver.resolve(request));
  }

  @Test
  public void login_registration_is_passed_through_untouched() {
    OAuth2AuthorizationRequest original = authorizationRequestFor("google");
    when(delegate.resolve(request)).thenReturn(original);

    assertSame(original, resolver.resolve(request));
  }

  @Test
  public void gmail_registration_is_refused_when_nobody_is_logged_in() {
    when(delegate.resolve(request)).thenReturn(authorizationRequestFor("gmail"));

    assertNull(resolver.resolve(request));
  }

  @Test
  public void gmail_registration_is_refused_for_a_regular_user() {
    SecurityContextHolder.getContext()
        .setAuthentication(new TestingAuthenticationToken("user", "pw", "ROLE_USER"));
    when(delegate.resolve(request)).thenReturn(authorizationRequestFor("gmail"));

    assertNull(resolver.resolve(request));
  }

  @Test
  public void gmail_registration_gets_offline_access_parameters_for_an_admin() {
    loginAsAdminWithEmail("admin@ucsb.edu");
    when(delegate.resolve(request)).thenReturn(authorizationRequestFor("gmail"));

    OAuth2AuthorizationRequest resolved = resolver.resolve(request);

    Map<String, Object> params = resolved.getAdditionalParameters();
    assertEquals("offline", params.get("access_type"));
    assertEquals("consent", params.get("prompt"));
    assertEquals("admin@ucsb.edu", params.get("login_hint"));
    assertEquals("state", resolved.getState());
  }

  @Test
  public void the_registration_id_overload_is_customized_too() {
    loginAsAdminWithEmail("admin@ucsb.edu");
    when(delegate.resolve(request, "gmail")).thenReturn(authorizationRequestFor("gmail"));

    OAuth2AuthorizationRequest resolved = resolver.resolve(request, "gmail");

    assertEquals("offline", resolved.getAdditionalParameters().get("access_type"));
  }

  @Test
  public void no_login_hint_when_the_principal_is_not_an_oauth2_user() {
    SecurityContextHolder.getContext()
        .setAuthentication(new TestingAuthenticationToken("admin", "pw", "ROLE_ADMIN"));
    when(delegate.resolve(request)).thenReturn(authorizationRequestFor("gmail"));

    OAuth2AuthorizationRequest resolved = resolver.resolve(request);

    assertEquals("offline", resolved.getAdditionalParameters().get("access_type"));
    assertFalse(resolved.getAdditionalParameters().containsKey("login_hint"));
  }
}
