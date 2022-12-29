package com.jug.worker;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;

@Configuration(proxyBeanMethods = false)
public class MicrometerBinderConfiguration {

    @Bean
    public MeterBinder numberOfPersons(PersonRepository repository) {
        return (registry) -> Gauge.builder("number.of.persons", repository::count).register(registry);
    }

}
