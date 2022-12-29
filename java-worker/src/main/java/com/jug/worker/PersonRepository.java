package com.jug.worker;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonRepository extends JpaRepository<Person, Long> {

    long countByFirstName(String firstName);
}