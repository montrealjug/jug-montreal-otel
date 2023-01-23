package com.jug.worker;

import java.util.logging.Logger;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduledTasks {

  private static final Logger LOGGER = Logger.getLogger(ScheduledTasks.class.getName());
  private final PersonRepository personRepository;

  public ScheduledTasks(PersonRepository personRepository) {
    this.personRepository = personRepository;
  }

  /**
   * Do a request at fixed interval to count the number of registered persons.
   */
  @Scheduled(fixedRate = 5000)
  public void executeSql() {
    if (personRepository.countByFirstName("jugmontreal") > 0) {
      // to have a log within a span
      LOGGER.info("jugmontreal person found");
    }
  }
}
