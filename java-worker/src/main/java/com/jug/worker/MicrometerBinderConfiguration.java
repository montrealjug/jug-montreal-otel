package com.jug.worker;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;

@Configuration(proxyBeanMethods = false)
public class MicrometerBinderConfiguration {

    /**
     * A micrometer Binder which returns the number of persons in database.
     */
    @Bean
    public MeterBinder numberOfPersons(PersonRepository repository) {
        // TOSHOW: micrometer gauge
        return (registry) -> Gauge.builder("jug.number.of.persons", repository::count).register(registry);
    }

}
