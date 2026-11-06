package edu.ucsb.cs156.spikegmail.config;

import edu.ucsb.cs156.spikegmail.services.GmailService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * Customizes the redirect to Google for the <code>gmail</code> registration only. The ordinary
 * <code>google</code> login registration is passed through untouched.
 *
 * <p>For <code>gmail</code> it:
 *
 * <ul>
 *   <li><b>refuses</b> (returns null, so nothing is redirected) unless the current user is an
 *       admin. The link on the home page is only shown to admins, but this is what actually stops
 *       a regular user from typing <code>/oauth2/authorization/gmail</code> into their browser.
 *   <li>adds <code>access_type=offline</code>, so that Google issues a refresh token. Without one,
 *       the app could only send mail for the first hour after the admin clicks the link.
 *   <li>adds <code>prompt=consent</code>, because Google only issues a refresh token the first
 *       time an account consents unless it is asked to show the consent screen again.
 *   <li>adds <code>login_hint</code> with the admin's email so Google pre-selects the account the
 *       admin is already logged in with.
 * </ul>
 */
public class GmailAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

  private final OAuth2AuthorizationRequestResolver delegate;

  public GmailAuthorizationRequestResolver(ClientRegistrationRepository clientRegistrationRepository) {
    this(
        new DefaultOAuth2AuthorizationRequestResolver(
            clientRegistrationRepository,
            OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI));
  }

  GmailAuthorizationRequestResolver(OAuth2AuthorizationRequestResolver delegate) {
    this.delegate = delegate;
  }

  @Override
  public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
    return customize(delegate.resolve(request));
  }

  @Override
  public OAuth2AuthorizationRequest resolve(
      HttpServletRequest request, String clientRegistrationId) {
    return customize(delegate.resolve(request, clientRegistrationId));
  }

  private OAuth2AuthorizationRequest customize(OAuth2AuthorizationRequest authorizationRequest) {
    if (authorizationRequest == null) {
      return null;
    }
    String registrationId = authorizationRequest.getAttribute(OAuth2ParameterNames.REGISTRATION_ID);
    if (!GmailService.REGISTRATION_ID.equals(registrationId)) {
      return authorizationRequest;
    }

    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (!isAdmin(authentication)) {
      return null;
    }

    String loginHint =
        authentication.getPrincipal() instanceof OAuth2User user
            ? user.getAttribute("email")
            : null;

    return OAuth2AuthorizationRequest.from(authorizationRequest)
        .additionalParameters(
            params -> {
              params.put("access_type", "offline");
              params.put("prompt", "consent");
              if (loginHint != null) {
                params.put("login_hint", loginHint);
              }
            })
        .build();
  }

  private static boolean isAdmin(Authentication authentication) {
    return authentication != null
        && authentication.getAuthorities().stream()
            .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
  }
}
