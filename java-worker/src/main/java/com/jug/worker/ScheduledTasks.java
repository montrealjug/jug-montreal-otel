package com.jug.worker;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduledTasks {

  private final PersonRepository personRepository;

  public ScheduledTasks(PersonRepository personRepository) {
    this.personRepository = personRepository;
  }

  /**
   * Do a request at fixed interval to count the number of registered persons.
   */
  @Scheduled(fixedRate = 5000)
  public void executeSql() {
    personRepository.countByFirstName("jugmontreal");
  }
}
