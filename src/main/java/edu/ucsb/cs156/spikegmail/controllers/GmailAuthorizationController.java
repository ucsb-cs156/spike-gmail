package edu.ucsb.cs156.spikegmail.controllers;

import edu.ucsb.cs156.spikegmail.entities.GmailSender;
import edu.ucsb.cs156.spikegmail.entities.User;
import edu.ucsb.cs156.spikegmail.repositories.GmailSenderRepository;
import edu.ucsb.cs156.spikegmail.services.CurrentUserService;
import edu.ucsb.cs156.spikegmail.services.GmailService;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * The last step of "authorize Gmail". The admin starts at <code>/oauth2/authorization/gmail</code>
 * (handled by Spring Security), consents at Google, and Google sends them back to <code>
 * /authorized/gmail?code=...</code>. Spring Security's authorization code grant filter exchanges
 * the code for tokens and stores them, and then redirects the browser to plain <code>
 * /authorized/gmail</code>, which is this method.
 *
 * <p>All this method has to do is check the result and record that this admin is now a sender.
 */
@Slf4j
@Controller
public class GmailAuthorizationController {

  private final CurrentUserService currentUserService;
  private final OAuth2AuthorizedClientService authorizedClientService;
  private final GmailService gmailService;
  private final GmailSenderRepository gmailSenderRepository;

  public GmailAuthorizationController(
      CurrentUserService currentUserService,
      OAuth2AuthorizedClientService authorizedClientService,
      GmailService gmailService,
      GmailSenderRepository gmailSenderRepository) {
    this.currentUserService = currentUserService;
    this.authorizedClientService = authorizedClientService;
    this.gmailService = gmailService;
    this.gmailSenderRepository = gmailSenderRepository;
  }

  @GetMapping("/authorized/gmail")
  public String authorized(RedirectAttributes redirectAttributes) {
    User user = currentUserService.getUser();
    String principalName = currentUserService.getPrincipalName();

    OAuth2AuthorizedClient client =
        authorizedClientService.loadAuthorizedClient(GmailService.REGISTRATION_ID, principalName);
    if (client == null) {
      redirectAttributes.addFlashAttribute(
          "error", "No Gmail authorization was found. Please try again.");
      return "redirect:/";
    }

    if (client.getRefreshToken() == null) {
      // Without a refresh token the app could only send mail for about an hour.
      discard(principalName);
      redirectAttributes.addFlashAttribute(
          "error", "Google did not issue a refresh token, so the authorization was discarded.");
      return "redirect:/";
    }

    // The admin could have picked a different Google account on Google's consent screen than the
    // one they are logged in to this app with. Mail would then come from an address that is not the
    // one displayed to users, so we check, and refuse if they differ.
    String authorizedEmail = gmailService.lookupEmail(client.getAccessToken().getTokenValue());
    if (authorizedEmail == null || !authorizedEmail.equalsIgnoreCase(user.getEmail())) {
      discard(principalName);
      redirectAttributes.addFlashAttribute(
          "error",
          "You authorized a different Google account ("
              + authorizedEmail
              + ") than the one you are logged in with ("
              + user.getEmail()
              + "). Please try again and choose "
              + user.getEmail()
              + ".");
      return "redirect:/";
    }

    GmailSender sender =
        gmailSenderRepository
            .findByEmail(user.getEmail())
            .orElseGet(() -> GmailSender.builder().email(user.getEmail()).build());
    sender.setPrincipalName(principalName);
    sender.setAuthorizedAt(LocalDateTime.now());
    gmailSenderRepository.save(sender);

    log.info("{} authorized sending email via Gmail", user.getEmail());
    redirectAttributes.addFlashAttribute(
        "message", "Done! Email can now be sent from " + user.getEmail() + ".");
    return "redirect:/";
  }

  private void discard(String principalName) {
    authorizedClientService.removeAuthorizedClient(GmailService.REGISTRATION_ID, principalName);
  }
}
