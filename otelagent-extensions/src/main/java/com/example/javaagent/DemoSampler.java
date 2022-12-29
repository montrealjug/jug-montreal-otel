/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.javaagent;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.sdk.trace.samplers.SamplingResult;
import java.util.List;

/**
 * This demo sampler filters out all internal spans whose name contains string "greeting".
 *
 * <p>See <a
 * href="https://github.com/open-telemetry/opentelemetry-specification/blob/master/specification/trace/sdk.md#sampling">
 * OpenTelemetry Specification</a> for more information about span sampling.
 *
 * @see DemoAutoConfigurationCustomizerProvider
 */
public class DemoSampler implements Sampler {
  @Override
  public SamplingResult shouldSample(
      Context parentContext,
      String traceId,
      String name,
      SpanKind spanKind,
      Attributes attributes,
      List<LinkData> parentLinks) {
      
    if (spanKind == SpanKind.INTERNAL && name.equals("ScheduledTasks.executeSql")) {
      return SamplingResult.drop();
    } else {
      // drop this span if parent hasn't been sampled
      SpanContext parentSpanContext = Span.fromContext(parentContext).getSpanContext();
      if (parentSpanContext.isValid() && !parentSpanContext.isSampled()) {
        return SamplingResult.drop();
      }
      return SamplingResult.recordAndSample();
    }
  }

  @Override
  public String getDescription() {
    return "DemoSampler";
  }
}
