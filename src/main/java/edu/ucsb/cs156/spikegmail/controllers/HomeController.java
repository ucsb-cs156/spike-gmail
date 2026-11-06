package edu.ucsb.cs156.spikegmail.controllers;

import edu.ucsb.cs156.spikegmail.entities.User;
import edu.ucsb.cs156.spikegmail.repositories.GmailSenderRepository;
import edu.ucsb.cs156.spikegmail.services.CurrentUserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** The one page of the app: a login link, or (when logged in) the Gmail actions available. */
@Controller
public class HomeController {

  private final CurrentUserService currentUserService;
  private final GmailSenderRepository gmailSenderRepository;

  public HomeController(
      CurrentUserService currentUserService, GmailSenderRepository gmailSenderRepository) {
    this.currentUserService = currentUserService;
    this.gmailSenderRepository = gmailSenderRepository;
  }

  @GetMapping("/")
  public String home(Model model) {
    User user = currentUserService.getUser();
    boolean loggedIn = user != null;
    model.addAttribute("loggedIn", loggedIn);

    if (loggedIn) {
      boolean admin = currentUserService.isAdmin();
      model.addAttribute("email", user.getEmail());
      model.addAttribute("admin", admin);
      model.addAttribute("senders", gmailSenderRepository.findAllByOrderByEmailAsc());
      model.addAttribute(
          "myAuthorization",
          admin && gmailSenderRepository.findByEmail(user.getEmail()).isPresent());
    }
    return "home";
  }
}
