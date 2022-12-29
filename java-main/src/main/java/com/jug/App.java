package com.jug;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

public class App {
    
    private static final Logger LOGGER = Logger.getLogger("App");
    private static final String JAVA_WORKER_URL = "http://localhost:8080/hello/";

    private final Random random = new Random();
    private final List<String> names = new ArrayList<>();
    private final Tracer tracer =
        GlobalOpenTelemetry.getTracer("java-main", "0.0.1");

    public static void main(String[] args) throws Exception {
        new App().start();
    }

    private void start() throws Exception {
        initNameList();
        
        while (true) {

            try {
                // Generator
                String firstname = localNameGenerator();

                // Call API
                doApiCall(firstname);
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "Got error: {0}:{1}", 
                    new String[]{ex.getClass().getName(), ex.getMessage()});
            }

            Thread.sleep(1000);
        }
    }

    private String localNameGenerator() {
        Span span = tracer.spanBuilder("localNameGenerator").startSpan();
        try (Scope ss = span.makeCurrent()) {
            return names.get(random.nextInt(names.size()));
        } finally {
            span.end();
        }
    }

    private void doApiCall(String firstname) throws Exception {
        Span span = tracer.spanBuilder("doApiCall").startSpan();
        span.setAttribute("firstname", firstname);
        try (Scope ss = span.makeCurrent()) {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(JAVA_WORKER_URL + firstname))
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            LOGGER.log(Level.INFO, "Response: {0}", response.body());
        } finally {
            span.end();
        }
    }

    private void initNameList() throws URISyntaxException, IOException {
        URL url = getClass().getClassLoader().getResource("names.txt");
        Path path = Paths.get(url.toURI());
        var lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        LOGGER.log(Level.INFO, "Got {0} names", lines.size());
        lines.stream().map(s -> s.toLowerCase()).forEach(names::add);
    }

}
