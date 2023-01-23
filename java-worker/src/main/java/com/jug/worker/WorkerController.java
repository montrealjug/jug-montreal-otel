package com.jug.worker;

import java.util.Random;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;

@RestController
public class WorkerController {

  private static final int FAIL_RATE_PERCENT = 3;

  private final Random random = new Random();
  private final PersonRepository repository;
  
  // micrometer registry
  private final MeterRegistry meterRegistry;

  public WorkerController(PersonRepository repository, MeterRegistry meterRegistry) {
    this.repository = repository;
    this.meterRegistry = meterRegistry;
  }

  /**
   * Create and return a person with the provided firstname
   * 
   * Randomly fail with a 500 error
   */
  @PostMapping("/person/{firstName}")
  public ResponseEntity<Person> createPerson(@PathVariable String firstName) {
    // TOSHOW: micrometer counter
    meterRegistry.counter("jug_create_request_total").increment();
    
    if (!randomlyFail(firstName)) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
    
    try {
      Person person = new Person(firstName);
      repository.save(person);
      return ResponseEntity.ok(person);
    } catch (org.springframework.dao.DataIntegrityViolationException ex) {
      return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }
  }

  /**
   * Return the person associated to the provided id, or 404 if not associated person found.
   */
  @GetMapping("/person/id/{id}")
  public ResponseEntity<Person> getPerson(@PathVariable long id) {
    // TOSHOW: micrometer counter
    meterRegistry.counter("jug_get_requests_total").increment();
    
    return repository.findById(id)
        .map(res -> ResponseEntity.ok(res))
        .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
  }

  /**
   * Randomly return {@code false} (odds are 1 on 30)
   */
  // TOSHOW: WithSpan and SpanAttribute comes from opentelemetry-instrumentation-annotations dependency
  @WithSpan
  private boolean randomlyFail(@SpanAttribute String firstName) {
    final int approximate_odds = 100 / FAIL_RATE_PERCENT;
    return (random.nextInt(approximate_odds) != 0);
  }

}
