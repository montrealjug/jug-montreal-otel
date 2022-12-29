package com.jug.worker;

import java.util.Random;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;

@RestController
public class WorkerController {

  private final Random random = new Random();
  private final PersonRepository repository;
  private final MeterRegistry meterRegistry;

  public WorkerController(PersonRepository repository, MeterRegistry meterRegistry) {
    this.repository = repository;
    this.meterRegistry = meterRegistry;
  }

  @GetMapping("/hello/{firstName}")
  public ResponseEntity<String> sayHello(@PathVariable String firstName) {
    meterRegistry.counter("hello_request_total").increment();
    
    if (!randomlyFail(firstName)) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body("random failure");
    }
    
    try {
      Person person = new Person(firstName);
      repository.save(person);
      return ResponseEntity
        .ok("Hello, " + firstName + " (id=" + person.getId() + ")!");
    } catch (org.springframework.dao.DataIntegrityViolationException ex) {
      return ResponseEntity.status(HttpStatus.CONFLICT)
        .body("'" + firstName + "' already exists in database");
    }
  }

  /**
   * Randomly return {@code false} (odds are 1 on 30)
   */
  @WithSpan
  private boolean randomlyFail(@SpanAttribute String firstName) {
    return (random.nextInt(30) != 0);
  }

}
