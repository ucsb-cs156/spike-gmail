package edu.ucsb.cs156.spikegmail.config;

import edu.ucsb.cs156.spikegmail.repositories.UserRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Web security: Google OAuth login for everybody, roles derived from ADMIN_EMAILS, and a second,
 * separate OAuth "client" flow (registration id <code>gmail</code>) that only admins can start.
 *
 * <p>The two flows are the heart of this spike:
 *
 * <ul>
 *   <li><code>oauth2Login()</code> authenticates the user, with basic scopes only (registration
 *       <code>google</code>). Nobody is asked to approve anything about Gmail here.
 *   <li><code>oauth2Client()</code> only <em>obtains and stores an access token</em> for an
 *       already-logged-in user (registration <code>gmail</code>). It does not log anybody in or
 *       change who is logged in.
 * </ul>
 */
@Configuration
@EnableWebSecurity
@Slf4j
public class SecurityConfig {

  private final UserRepository userRepository;
  private final List<String> adminEmails;

  public SecurityConfig(
      UserRepository userRepository, @Value("${app.admin.emails}") List<String> adminEmails) {
    this.userRepository = userRepository;
    this.adminEmails = adminEmails;
  }

  @Bean
  public SecurityFilterChain filterChain(
      HttpSecurity http, OAuth2AuthorizationRequestResolver authorizationRequestResolver)
      throws Exception {
    http.authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/", "/error")
                    .permitAll()
                    // Where Google sends the admin back to after the gmail consent screen.
                    .requestMatchers("/authorized/gmail")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .authenticated())
        .oauth2Login(
            oauth2 ->
                oauth2
                    // Unauthenticated users are sent to the home page, which has the login link.
                    .loginPage("/")
                    .authorizationEndpoint(
                        endpoint ->
                            endpoint.authorizationRequestResolver(authorizationRequestResolver))
                    .userInfoEndpoint(
                        userInfo -> userInfo.userAuthoritiesMapper(this.userAuthoritiesMapper())))
        .oauth2Client(
            client ->
                client.authorizationCodeGrant(
                    grant -> grant.authorizationRequestResolver(authorizationRequestResolver)))
        .logout(logout -> logout.logoutSuccessUrl("/"));
    return http.build();
  }

  private GrantedAuthoritiesMapper userAuthoritiesMapper() {
    return (authorities) -> {
      Set<GrantedAuthority> mappedAuthorities = new HashSet<>();
      authorities.forEach(
          authority -> {
            mappedAuthorities.add(authority);
            if (authority instanceof OAuth2UserAuthority oauth2UserAuthority) {
              Map<String, Object> userAttributes = oauth2UserAuthority.getAttributes();
              mappedAuthorities.add(new SimpleGrantedAuthority("ROLE_USER"));

              String email = (String) userAttributes.get("email");
              if (getAdmin(email)) {
                mappedAuthorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
              }
            }
          });
      log.info("mappedAuthorities={}", mappedAuthorities);
      return mappedAuthorities;
    };
  }

  /**
   * Whether the given email belongs to an admin, either because it is listed in ADMIN_EMAILS or
   * because the users table says so.
   *
   * @param email email address of the user
   * @return whether the user with the given email is an admin
   */
  public boolean getAdmin(String email) {
    if (adminEmails.contains(email)) {
      return true;
    }
    return userRepository.findByEmail(email).map(u -> u.getAdmin()).orElse(false);
  }
}
