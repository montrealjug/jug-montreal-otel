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

  @Scheduled(fixedRate = 5000)
  public void executeSql() {
    if (personRepository.countByFirstName("jugmontreal") > 0) {
        //LOGGER.info("jugmontreal person found");
    }
  }
}
