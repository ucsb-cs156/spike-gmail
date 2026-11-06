package edu.ucsb.cs156.spikegmail.services;

import edu.ucsb.cs156.spikegmail.entities.User;
import edu.ucsb.cs156.spikegmail.repositories.UserRepository;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

/** Provides information about the user that is logged in with Google OAuth, if any. */
@Slf4j
@Service
public class CurrentUserService {

  private final UserRepository userRepository;
  private final List<String> adminEmails;

  public CurrentUserService(
      UserRepository userRepository, @Value("${app.admin.emails}") List<String> adminEmails) {
    this.userRepository = userRepository;
    this.adminEmails = adminEmails;
  }

  /**
   * Returns the logged in user, or null if nobody is logged in.
   *
   * <p>Side effect: a user that has not been seen before is stored in the database, and a user
   * whose email is in ADMIN_EMAILS is marked as an admin.
   *
   * @return the current user, or null
   */
  public User getUser() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication instanceof AnonymousAuthenticationToken) {
      return null;
    }
    if (authentication instanceof OAuth2AuthenticationToken token) {
      return findOrCreateUser(token.getPrincipal());
    }
    return null;
  }

  /**
   * Whether the current user has ROLE_ADMIN.
   *
   * @return true if logged in as an admin
   */
  public boolean isAdmin() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication != null
        && authentication.getAuthorities().stream()
            .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
  }

  /**
   * The Spring Security principal name of the current user. For Google login this is the Google
   * "sub", and it is the key under which the user's OAuth2 tokens are stored.
   *
   * @return the principal name, or null if nobody is logged in
   */
  public String getPrincipalName() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication == null ? null : authentication.getName();
  }

  private User findOrCreateUser(OAuth2User oAuthUser) {
    String email = oAuthUser.getAttribute("email");

    Optional<User> existing = userRepository.findByEmail(email);
    if (existing.isPresent()) {
      User u = existing.get();
      if (adminEmails.contains(email) && !u.getAdmin()) {
        u.setAdmin(true);
        userRepository.save(u);
      }
      return u;
    }

    User u =
        User.builder()
            .googleSub(oAuthUser.getAttribute("sub"))
            .email(email)
            .pictureUrl(oAuthUser.getAttribute("picture"))
            .fullName(oAuthUser.getAttribute("name"))
            .givenName(oAuthUser.getAttribute("given_name"))
            .familyName(oAuthUser.getAttribute("family_name"))
            .emailVerified(Boolean.TRUE.equals(oAuthUser.getAttribute("email_verified")))
            .locale(oAuthUser.getAttribute("locale"))
            .hostedDomain(oAuthUser.getAttribute("hd"))
            .admin(adminEmails.contains(email))
            .build();
    userRepository.save(u);
    return u;
  }
}
