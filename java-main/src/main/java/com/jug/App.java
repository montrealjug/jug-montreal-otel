package com.jug;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

public class App {
    
    private static final Logger LOGGER = Logger.getLogger("App");
    private static final String CREATE_PERSON_ENDPOINT = "http://localhost:8080/person/";
    private static final String GET_PERSON_BY_ID_ENDPOINT = "http://localhost:8080/person/id/";

    // p = already in database, with rate = 1/sec and size = 1000
    // after 5min, p=30%
    private static final int NAMES_LIST_SIZE = 1000;
    private static final int POST_RATE_MS = 1000;

    private final Random random = new Random();
    private final List<String> names = new ArrayList<>();
    private final Tracer tracer =
        GlobalOpenTelemetry.getTracer("java-main", "0.0.1");
    private final Meter meter =
        GlobalOpenTelemetry.getMeter("java-worker");
    private final ScheduledExecutorService pool = Executors.newSingleThreadScheduledExecutor();
    private final static Pattern UGLY_JSON_PATTERN = Pattern.compile("\"id\":(\\d+)");

    private final AtomicLong numberOfSuccessfulCreatedRequests = new AtomicLong(0);

    public static void main(String[] args) throws Exception {
        new App().start();
    }

    /**
     * Create regularly a person and try to fetch a random person
     */
    private void start() throws Exception {
        initNameList();

        pool.scheduleAtFixedRate(this::createPerson, 0, POST_RATE_MS, TimeUnit.MILLISECONDS);
        pool.scheduleAtFixedRate(new GetPersonJob(), 0, POST_RATE_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * Get a random person on the worker backend
     * 
     * May fail is the person already exists or the service randomly fails
     */
    private void createPerson() {
        try {
            // Generator
            String firstname = localNameGenerator();

            // Call API
            doCreateCall(firstname);
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Got error: {0}:{1}", 
                new String[]{ex.getClass().getName(), ex.getMessage()});
        }
    }

    /**
     * Return a random name from the list within a dedicated span
     */
    private String localNameGenerator() {
        Span span = tracer.spanBuilder("localNameGenerator").startSpan();
        try (Scope ss = span.makeCurrent()) {
            int randomValue = random.nextInt(names.size());
            LongHistogram histogram = meter.histogramBuilder("jug_local_name_generator").setUnit("idx").ofLongs().build();
            histogram.record(randomValue);
            return names.get(randomValue);
        } finally {
            span.end();
        }
    }

    /**
     * Do a http request to create a person on the worker backend, then register a task to retrieve this person from the worker
     * 
     * It illustrates a nested span, a linked span from the asynchronous task, a span attribute, a span event
     */
    private void doCreateCall(String firstname) throws Exception {
        // TOSHOW: span creation and span attribute
        Span span = tracer.spanBuilder("doCreateCall").startSpan();
        span.setAttribute("firstname", firstname);
        try (Scope ss = span.makeCurrent()) {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(CREATE_PERSON_ENDPOINT + firstname))
                .POST(BodyPublishers.noBody())
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                var body = response.body();
                LOGGER.log(Level.INFO, "Person created: {0}", body);
                numberOfSuccessfulCreatedRequests.incrementAndGet();
                Matcher matcher = UGLY_JSON_PATTERN.matcher(body);
                if (matcher.find()) {
                    long id = Long.valueOf(matcher.group(1));
                    pool.schedule(new GetPersonJob(span.getSpanContext(), id), 0, TimeUnit.MILLISECONDS);
                    // TOSHOW: span event
                    span.addEvent("GetPersonJob scheduled", Attributes.of(AttributeKey.longKey("id"), id));
                }
            } else {
                // TOSHOW: span in error (ok by default so no need for the normal case)
                span.setStatus(StatusCode.ERROR, "Got http code " + response.statusCode());
            }
        } finally {
            // TOSHOW: always end the span to avoid leak
            span.end();
        }
    }

    /**
     * Creates a names list from the resource file named {@code names.txt}
     */
    private void initNameList() throws URISyntaxException, IOException {
        URL url = getClass().getClassLoader().getResource("names.txt");
        Path path = Paths.get(url.toURI());
        var lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        LOGGER.log(Level.INFO, "Got {0} names", lines.size());
        lines.stream().limit(NAMES_LIST_SIZE).map(s -> s.toLowerCase()).forEach(names::add);
    }

    /**
     * Runnable instance to get a random or specified user from the worker
     * 
     * It is made to illustrate a span in a periodic job
     */
    private class GetPersonJob implements Runnable {

        private final long id;
        private final SpanContext spanContext;

        public GetPersonJob(SpanContext spanContext, long id) {
            this.spanContext = spanContext;
            this.id = id;
        }

        public GetPersonJob() {
            this.spanContext = null;
            this.id = 0;
        }

        @Override
        public void run() {
            SpanBuilder builder = tracer.spanBuilder("getPerson");
            if (spanContext != null) {
                // TOSHOW: link with the main span
                builder.addLink(spanContext);
            }
            Span span = builder.startSpan();
            try (Scope ss = span.makeCurrent()) {
                long param;
                if (this.id == 0) {
                    param = random.nextLong(NAMES_LIST_SIZE / 10);
                } else {
                    param = this.id;
                }
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GET_PERSON_BY_ID_ENDPOINT + param))
                    .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                LOGGER.log(Level.INFO, "Response: {0}:{1}", new String[]{String.valueOf(response.statusCode()), response.body()});
            } catch(Exception ex) {
                LOGGER.log(Level.SEVERE, "Got error: {0}:{1}", 
                    new String[]{ex.getClass().getName(), ex.getMessage()});
                // TOSHOW: span with exception
                span.recordException(ex);
                span.setStatus(StatusCode.ERROR);
            } finally {
                span.end();
            }
        }

    }

}
