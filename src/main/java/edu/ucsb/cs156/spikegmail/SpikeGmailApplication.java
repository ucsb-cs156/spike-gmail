package edu.ucsb.cs156.spikegmail;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Entry point for the spike-gmail application. */
@SpringBootApplication
public class SpikeGmailApplication {

  /**
   * The main method is the entry point for the application.
   *
   * @param args command line arguments, typically unused for Spring Boot applications
   */
  public static void main(String[] args) {
    SpringApplication.run(SpikeGmailApplication.class, args);
  }
}
