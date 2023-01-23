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
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;

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
    
    private OpenTelemetry openTelemetry;
    private Tracer tracer;
    private Meter meter;

    private final ScheduledExecutorService pool = Executors.newSingleThreadScheduledExecutor();
    private final static Pattern UGLY_JSON_PATTERN_TO_FIND_ID = Pattern.compile("\"id\":(\\d+)");

    public static void main(String[] args) throws Exception {
        new App().start();
    }

    /**
     * Create regularly a person and try to fetch a random person
     */
    private void start() throws Exception {
        // TOSHOW: otel exporter manual configuration
        OtlpGrpcSpanExporter otelSpanExporter = OtlpGrpcSpanExporter.builder()
            .setEndpoint("http://localhost:4317")
            .build();

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(BatchSpanProcessor.builder(otelSpanExporter).build())
            .setResource(Resource.create(Attributes.of(AttributeKey.stringKey("service.name"), "java-main")))
            .build();

        OtlpGrpcMetricExporter otelMetricExporter = OtlpGrpcMetricExporter.builder()
            .setEndpoint("http://localhost:4317")
            .build();
        
        SdkMeterProvider meterProvider = SdkMeterProvider.builder()
            .registerMetricReader(PeriodicMetricReader.builder(otelMetricExporter).build())
            .setResource(Resource.create(Attributes.of(AttributeKey.stringKey("service.name"), "java-main")))
            .build();

        openTelemetry = OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setMeterProvider(meterProvider)
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .build();
        tracer = openTelemetry.getTracer("java-main", "0.0.1");
        meter = openTelemetry.getMeter("java-main");
        
        // with agent
        // tracer = GlobalOpenTelemetry.getTracer("java-main", "0.0.1");
        // meter = GlobalOpenTelemetry.getMeter("java-worker");
        //openTelemetry = GlobalOpenTelemetry.get();

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
        Span span = tracer.spanBuilder("createPerson").startSpan();
        try (Scope ss = span.makeCurrent()) {
            // Generator
            String firstname = localNameGenerator();

            // Call API
            doCreateCall(firstname);
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Got error: {0}:{1}", 
                new String[]{ex.getClass().getName(), ex.getMessage()});
            span.recordException(ex);
            span.setStatus(StatusCode.ERROR);
        } finally {
            span.end();
        }
    }

    /**
     * Return a random name from the list within a dedicated span
     */
    private String localNameGenerator() {
        // TODO@Maxime: remote call to a rust app with a manual propagation of the context (traceid)
        Span span = tracer.spanBuilder("localNameGenerator").setSpanKind(SpanKind.INTERNAL).startSpan();
        try (Scope ss = span.makeCurrent()) {
            int randomValue = random.nextInt(names.size());
            String name = names.get(randomValue);
            meter.counterBuilder("jug_name_generator_triggered").build().add(1L);
            return name;
        } finally {
            span.end();
        }
    }

    /**
     * Insert into the HttpRequest the trace context
     */
    private static final TextMapSetter<HttpRequest.Builder> PROPAGATOR_TEXTMAP_SETTER = new TextMapSetter<HttpRequest.Builder>() {
        @Override
        public void set(HttpRequest.Builder carrier, String key, String value) {
            // TOSHOW: This adds traceparent=XXXXX as an header
            // example: 00-7b5bb1484ab0e58d43e272aa24bee285-e9f4eaaac5de987b-01
            // version "-" trace-id "-" parent-id "-" trace-flags
            // see https://www.w3.org/TR/trace-context/#traceparent-header-field-values
            carrier.header(key, value);
        }
    };
    
    private HttpRequest.Builder addContextToHttpRequest(HttpRequest.Builder requestBuilder) {
        openTelemetry.getPropagators().getTextMapPropagator().inject(Context.current(), requestBuilder, PROPAGATOR_TEXTMAP_SETTER);
        return requestBuilder;
    }   

    /**
     * Do a http request to create a person on the worker backend, then register a task to retrieve this person from the worker
     * 
     * It illustrates a nested span, a linked span from the asynchronous task, a span attribute, a span event
     */
    private void doCreateCall(String firstname) throws Exception {
        // TOSHOW: span creation and span attribute
        Span span = tracer.spanBuilder("doCreateCall").setSpanKind(SpanKind.CLIENT).startSpan();
        span.setAttribute("firstname", firstname);
        try (Scope ss = span.makeCurrent()) {
            HttpClient client = HttpClient.newHttpClient();
            
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(CREATE_PERSON_ENDPOINT + firstname))
                .POST(BodyPublishers.noBody());
            
            HttpResponse<String> response = client.send(addContextToHttpRequest(requestBuilder).build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                span.setStatus(StatusCode.OK);
                var body = response.body();
                LOGGER.log(Level.INFO, "Person created: {0}", body);
                getIdFromResponse(body).ifPresent(id -> {
                    pool.schedule(new GetPersonJob(span.getSpanContext(), id), 0, TimeUnit.MILLISECONDS);
                    // TOSHOW: span event
                    span.addEvent("GetPersonJob scheduled", Attributes.of(AttributeKey.longKey("id"), id));
                });
            } else {
                // TOSHOW: span with error status
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
     * Retrieve id from json response
     */
    private Optional<Long> getIdFromResponse(String body) {
        Matcher matcher = UGLY_JSON_PATTERN_TO_FIND_ID.matcher(body);
        if (matcher.find()) {
            return Optional.of(Long.valueOf(matcher.group(1)));
        }
        return Optional.empty();
    }        

    /**
     * Runnable instance to get a random or specified user from the worker
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
            SpanBuilder builder = tracer.spanBuilder("getPerson").setSpanKind(SpanKind.CLIENT);
            if (spanContext != null) {
                // TOSHOW: link with the main span
                builder.addLink(spanContext);
                builder.setAttribute("withlink", "true");
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
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(GET_PERSON_BY_ID_ENDPOINT + param));

                HttpResponse<String> response = client.send(addContextToHttpRequest(requestBuilder).build(), HttpResponse.BodyHandlers.ofString());
                LOGGER.log(Level.INFO, "Response: {0}:{1}", new String[]{String.valueOf(response.statusCode()), response.body()});
                // do not set status.OK on successful span according to doc
            } catch(Exception ex) {
                LOGGER.log(Level.SEVERE, "Got error: {0}:{1}", 
                    new String[]{ex.getClass().getName(), ex.getMessage()});
                // TOSHOW: span with exception
                span.recordException(ex);
                // should be the last call before ending the span
                span.setStatus(StatusCode.ERROR);
            } finally {
                span.end();
            }
        }
    }
}
