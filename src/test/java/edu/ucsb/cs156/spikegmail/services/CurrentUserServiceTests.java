package edu.ucsb.cs156.spikegmail.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.ucsb.cs156.spikegmail.entities.User;
import edu.ucsb.cs156.spikegmail.repositories.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

public class CurrentUserServiceTests {

  private UserRepository userRepository;
  private CurrentUserService service;

  @BeforeEach
  public void setUp() {
    userRepository = mock(UserRepository.class);
    service = new CurrentUserService(userRepository, List.of("admin@ucsb.edu"));
  }

  @AfterEach
  public void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private void loginAs(String email, String... roles) {
    DefaultOAuth2User principal =
        new DefaultOAuth2User(
            AuthorityUtils.createAuthorityList(roles),
            Map.of(
                "sub", "sub-" + email,
                "email", email,
                "name", "Some Body",
                "email_verified", true),
            "sub");
    SecurityContextHolder.getContext()
        .setAuthentication(
            new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google"));
  }

  @Test
  public void no_authentication_means_no_user() {
    assertNull(service.getUser());
    assertFalse(service.isAdmin());
    assertNull(service.getPrincipalName());
  }

  @Test
  public void anonymous_means_no_user() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new AnonymousAuthenticationToken(
                "key", "anon", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

    assertNull(service.getUser());
    assertFalse(service.isAdmin());
  }

  @Test
  public void a_non_oauth_authentication_means_no_user() {
    SecurityContextHolder.getContext()
        .setAuthentication(new TestingAuthenticationToken("u", "p", "ROLE_USER"));

    assertNull(service.getUser());
  }

  @Test
  public void a_new_user_is_stored() {
    loginAs("user@ucsb.edu", "ROLE_USER");
    when(userRepository.findByEmail("user@ucsb.edu")).thenReturn(Optional.empty());

    User u = service.getUser();

    assertEquals("user@ucsb.edu", u.getEmail());
    assertEquals("sub-user@ucsb.edu", u.getGoogleSub());
    assertTrue(u.getEmailVerified());
    assertFalse(u.getAdmin());
    verify(userRepository).save(u);
  }

  @Test
  public void a_new_user_listed_in_admin_emails_is_an_admin() {
    loginAs("admin@ucsb.edu", "ROLE_USER");
    when(userRepository.findByEmail("admin@ucsb.edu")).thenReturn(Optional.empty());

    assertTrue(service.getUser().getAdmin());
  }

  @Test
  public void an_existing_user_is_returned_as_is() {
    loginAs("user@ucsb.edu", "ROLE_USER");
    User existing = User.builder().id(3L).email("user@ucsb.edu").build();
    when(userRepository.findByEmail("user@ucsb.edu")).thenReturn(Optional.of(existing));

    assertEquals(existing, service.getUser());
    verify(userRepository, never()).save(any());
  }

  @Test
  public void an_existing_user_that_is_now_listed_in_admin_emails_is_promoted() {
    loginAs("admin@ucsb.edu", "ROLE_USER");
    User existing = User.builder().id(3L).email("admin@ucsb.edu").admin(false).build();
    when(userRepository.findByEmail("admin@ucsb.edu")).thenReturn(Optional.of(existing));

    service.getUser();

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(captor.capture());
    assertTrue(captor.getValue().getAdmin());
  }

  @Test
  public void an_existing_admin_is_not_saved_again() {
    loginAs("admin@ucsb.edu", "ROLE_USER", "ROLE_ADMIN");
    User existing = User.builder().id(3L).email("admin@ucsb.edu").admin(true).build();
    when(userRepository.findByEmail("admin@ucsb.edu")).thenReturn(Optional.of(existing));

    service.getUser();

    verify(userRepository, never()).save(any());
  }

  @Test
  public void isAdmin_and_principal_name_come_from_the_authentication() {
    loginAs("admin@ucsb.edu", "ROLE_USER", "ROLE_ADMIN");

    assertTrue(service.isAdmin());
    assertEquals("sub-admin@ucsb.edu", service.getPrincipalName());
  }

  @Test
  public void a_regular_user_is_not_an_admin() {
    loginAs("user@ucsb.edu", "ROLE_USER");

    assertFalse(service.isAdmin());
  }
}
