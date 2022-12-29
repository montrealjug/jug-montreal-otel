---
marp: true
theme: gaia
_class: lead
paginate: true
backgroundColor: #fff
backgroundImage: url('https://marp.app/assets/hero-background.svg')
---

![bg left:40% 80%](https://cncf-branding.netlify.app/img/projects/opentelemetry/icon/color/opentelemetry-icon-color.png)

![bg left:40% 80%](jug-logo.png)

# **Open Telemetry**

**A framework to monitor them all ?**

---

# Who are we ?

Olivier Gatimel, Java developer since 2009
Currently principal dev at CARL Software, an EAM software editor

<!-- TODO Maxime -->

---

# Agenda

* History of the project
* What OpenTelemetry is and what it is **not**
* Is it production ready in 2023 ?
<!-- @Maxime: à compléter par le support côté exporter chez certains éditeurs ?  -->
* Examples with the Java API to add traces, metrics, baggages
* Some usage and tuning with the Java agent
<!-- @Maxime: n'hésite pas à mettre tes idées (rust par exemple ;) ) -->

---

# OpenTelemetry history

_Yet another telemetry framework ?_

Started in mid 2019 as a sandbox merge project of OpenTracing (traces) and OpenCensus (metrics) supported by CNCF
Switched to incubating project in mid 2021
Second most important project in CNCF (after Kubernetes)

---

# What it is

Signals :
* Tracing (propagation)
* Metrics
* Logs (later)

# What it is **not**

A tool to store and visualize traces, metrics or logs

---

# What does it provide

* Specification
* API and SDK in many languages (Java, Python, Rust, ...)
* SDK configuration is using centralized configuration variables
* Collector : aka OtelCol, written in go, can have an authenticator
* Protocol : aka OTLP, over HTTP or gRPC or JSON Protobuff (experimental)

---

# Glossary

https://opentelemetry.io/docs/concepts/glossary/

---

# Trace, span

<!-- trouver des images illustrant trace et span -->

---

# Logs

<!-- je pense ne pas l'aborder car encore expérimental -->

---

# Baggage

Caution with visiblity as it is part of the context propagation
Not a span attribute, so it must be copied

---

![bg 80%](https://opentelemetry.io/img/otel_diagram.png)

---

![bg 80%](https://raw.github.com/open-telemetry/opentelemetry.io/main/iconography/Otel_Collector.svg)

---

# Is it ready ?

Current status : https://opentelemetry.io/status/

Tracing: Stable with LTS and version 1.x out since Feb 2021


Metric: API and protocol stable since Nov 2021
https://github.com/open-telemetry/opentelemetry-specification/pull/2104
SDK still under active dev, but Meter is ready in java-sdk



Logs: draft/experimental
Integration with existing framework first, then logging API later

---

Implementation vs Specification : https://github.com/open-telemetry/opentelemetry-specification/blob/main/spec-compliance-matrix.md

Java is usually the first language to get things implemented first

---

# Migration

* From OpenTracing : https://github.com/open-telemetry/opentelemetry-java/blob/main/opentracing-shim
* From OpenCensus : https://github.com/open-telemetry/opentelemetry-java/blob/main/opencensus-shim

--- 

From micrometer :
* with Java agent : https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/instrumentation/micrometer/micrometer-1.5
* from OTEL to micrometer : https://github.com/open-telemetry/opentelemetry-java-contrib/tree/main/micrometer-meter-provider
* exporting OTLP over HTTP : https://github.com/micrometer-metrics/micrometer/issues/2864

---

It is moving fast. So keep a look at new features or configuration params regularly.

---

# How context is propagated ?

By default,
* W3C Trace Context (https://www.w3.org/TR/trace-context/) -> published in november 2021
* W3C Baggage (https://www.w3.org/TR/baggage/) -> working draft since september 2022

---

when not to instrument : https://opentelemetry.io/docs/concepts/instrumenting-library/#when-not-to-instrument

<!-- @Maxime : si tu as envie d'apporter ton expérience sur ce point, sinon
je pensais mettre ça uniquement dans les liens à la fin -->

---

# Java API

Depends on API only (unless specific needs)

<!-- demo with java-main project -->

---

# Java agent

Still in beta but usable
Maintened mostly by trask, the developer of Glowroot

Alternative ?

Add java-sdk dependency and initialize AutoConfigure
https://github.com/open-telemetry/opentelemetry-java-docs/blob/main/autoconfigure/src/main/java/io/opentelemetry/example/autoconfigure/AutoConfigExample.java

<!-- demo with java-main project>

<!-- TODO
Add trace_id+span_id to MDC logging : https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/docs/logger-mdc-instrumentation.md
-->

---

# Spring Boot

https://spring.io/blog/2022/10/12/observability-with-spring-boot-3

<!-- Demo with java-worker and micrometer bridge -->

---

# Java agent extensions

Can tune the agent with external jars

<!-- Demo with otelagent-extensions project -->

---

# Some links to go deeper

8-parts guide about OTEL: https://www.apmdigest.com/opentelemetry-1
test with traces: https://github.com/kubeshop/tracetest
