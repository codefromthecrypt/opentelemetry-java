/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.exporter.otlp.http.metrics;

import static io.opentelemetry.api.internal.Utils.checkArgument;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.exporter.internal.compression.CompressorUtil;
import io.opentelemetry.exporter.internal.http.HttpExporterBuilder;
import io.opentelemetry.exporter.internal.otlp.metrics.MetricsRequestMarshaler;
import io.opentelemetry.exporter.otlp.internal.OtlpUserAgent;
import io.opentelemetry.sdk.common.export.RetryPolicy;
import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.export.AggregationTemporalitySelector;
import io.opentelemetry.sdk.metrics.export.DefaultAggregationSelector;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;

/**
 * Builder utility for {@link OtlpHttpMetricExporter}.
 *
 * @since 1.14.0
 */
public final class OtlpHttpMetricExporterBuilder {

  private static final String DEFAULT_ENDPOINT = "http://localhost:4318/v1/metrics";

  private static final AggregationTemporalitySelector DEFAULT_AGGREGATION_TEMPORALITY_SELECTOR =
      AggregationTemporalitySelector.alwaysCumulative();

  private final HttpExporterBuilder<MetricsRequestMarshaler> delegate;
  private AggregationTemporalitySelector aggregationTemporalitySelector =
      DEFAULT_AGGREGATION_TEMPORALITY_SELECTOR;

  private DefaultAggregationSelector defaultAggregationSelector =
      DefaultAggregationSelector.getDefault();

  OtlpHttpMetricExporterBuilder(HttpExporterBuilder<MetricsRequestMarshaler> delegate) {
    this.delegate = delegate;
    delegate.setMeterProvider(MeterProvider::noop);
    OtlpUserAgent.addUserAgentHeader(delegate::addConstantHeaders);
  }

  OtlpHttpMetricExporterBuilder() {
    this(new HttpExporterBuilder<>("otlp", "metric", DEFAULT_ENDPOINT));
  }

  /**
   * Sets the maximum time to wait for the collector to process an exported batch of metrics. If
   * unset, defaults to {@value HttpExporterBuilder#DEFAULT_TIMEOUT_SECS}s.
   */
  public OtlpHttpMetricExporterBuilder setTimeout(long timeout, TimeUnit unit) {
    requireNonNull(unit, "unit");
    checkArgument(timeout >= 0, "timeout must be non-negative");
    delegate.setTimeout(timeout, unit);
    return this;
  }

  /**
   * Sets the maximum time to wait for the collector to process an exported batch of metrics. If
   * unset, defaults to {@value HttpExporterBuilder#DEFAULT_TIMEOUT_SECS}s.
   */
  public OtlpHttpMetricExporterBuilder setTimeout(Duration timeout) {
    requireNonNull(timeout, "timeout");
    return setTimeout(timeout.toNanos(), TimeUnit.NANOSECONDS);
  }

  /**
   * Sets the maximum time to wait for new connections to be established. If unset, defaults to
   * {@value HttpExporterBuilder#DEFAULT_CONNECT_TIMEOUT_SECS}s.
   *
   * @since 1.33.0
   */
  public OtlpHttpMetricExporterBuilder setConnectTimeout(long timeout, TimeUnit unit) {
    requireNonNull(unit, "unit");
    checkArgument(timeout >= 0, "timeout must be non-negative");
    delegate.setConnectTimeout(timeout, unit);
    return this;
  }

  /**
   * Sets the maximum time to wait for new connections to be established. If unset, defaults to
   * {@value HttpExporterBuilder#DEFAULT_CONNECT_TIMEOUT_SECS}s.
   *
   * @since 1.33.0
   */
  public OtlpHttpMetricExporterBuilder setConnectTimeout(Duration timeout) {
    requireNonNull(timeout, "timeout");
    return setConnectTimeout(timeout.toNanos(), TimeUnit.NANOSECONDS);
  }

  /**
   * Sets the OTLP endpoint to connect to. If unset, defaults to {@value DEFAULT_ENDPOINT}. The
   * endpoint must start with either http:// or https://, and include the full HTTP path.
   */
  public OtlpHttpMetricExporterBuilder setEndpoint(String endpoint) {
    requireNonNull(endpoint, "endpoint");
    delegate.setEndpoint(endpoint);
    return this;
  }

  /**
   * Sets the method used to compress payloads. If unset, compression is disabled. Currently
   * supported compression methods include "gzip" and "none".
   */
  public OtlpHttpMetricExporterBuilder setCompression(String compressionMethod) {
    requireNonNull(compressionMethod, "compressionMethod");
    if (compressionMethod.equals("none")) {
      delegate.setCompression(null);
      return this;
    }
    Set<String> supportedCompressionMethods = CompressorUtil.supportedCompressors();
    checkArgument(
        supportedCompressionMethods.contains(compressionMethod),
        "Unsupported compressionMethod. Compression method must be \"none\" or one of: "
            + supportedCompressionMethods.stream().collect(joining(",", "[", "]")));
    delegate.setCompression(CompressorUtil.resolveCompressor(compressionMethod));
    return this;
  }

  /**
   * Add a constant header to requests. If the {@code key} collides with another constant header
   * name or a one from {@link #setHeaders(Supplier)}, the values from both are included.
   */
  public OtlpHttpMetricExporterBuilder addHeader(String key, String value) {
    delegate.addConstantHeaders(key, value);
    return this;
  }

  /**
   * Set the supplier of headers to add to requests. If a key from the map collides with a constant
   * from {@link #addHeader(String, String)}, the values from both are included.
   *
   * @since 1.33.0
   */
  public OtlpHttpMetricExporterBuilder setHeaders(Supplier<Map<String, String>> headerSupplier) {
    delegate.setHeadersSupplier(headerSupplier);
    return this;
  }

  /**
   * Sets the certificate chain to use for verifying servers when TLS is enabled. The {@code byte[]}
   * should contain an X.509 certificate collection in PEM format. If not set, TLS connections will
   * use the system default trusted certificates.
   */
  public OtlpHttpMetricExporterBuilder setTrustedCertificates(byte[] trustedCertificatesPem) {
    delegate.setTrustManagerFromCerts(trustedCertificatesPem);
    return this;
  }

  /**
   * Sets ths client key and the certificate chain to use for verifying client when TLS is enabled.
   * The key must be PKCS8, and both must be in PEM format.
   */
  public OtlpHttpMetricExporterBuilder setClientTls(byte[] privateKeyPem, byte[] certificatePem) {
    delegate.setKeyManagerFromCerts(privateKeyPem, certificatePem);
    return this;
  }

  /**
   * Sets the "bring-your-own" SSLContext for use with TLS. Users should call this _or_ set raw
   * certificate bytes, but not both.
   *
   * @since 1.26.0
   */
  public OtlpHttpMetricExporterBuilder setSslContext(
      SSLContext sslContext, X509TrustManager trustManager) {
    delegate.setSslContext(sslContext, trustManager);
    return this;
  }

  /**
   * Set the {@link AggregationTemporalitySelector} used for {@link
   * MetricExporter#getAggregationTemporality(InstrumentType)}.
   *
   * <p>If unset, defaults to {@link AggregationTemporalitySelector#alwaysCumulative()}.
   *
   * <p>{@link AggregationTemporalitySelector#deltaPreferred()} is a common configuration for delta
   * backends.
   */
  public OtlpHttpMetricExporterBuilder setAggregationTemporalitySelector(
      AggregationTemporalitySelector aggregationTemporalitySelector) {
    requireNonNull(aggregationTemporalitySelector, "aggregationTemporalitySelector");
    this.aggregationTemporalitySelector = aggregationTemporalitySelector;
    return this;
  }

  /**
   * Set the {@link DefaultAggregationSelector} used for {@link
   * MetricExporter#getDefaultAggregation(InstrumentType)}.
   *
   * <p>If unset, defaults to {@link DefaultAggregationSelector#getDefault()}.
   *
   * @since 1.16.0
   */
  public OtlpHttpMetricExporterBuilder setDefaultAggregationSelector(
      DefaultAggregationSelector defaultAggregationSelector) {
    requireNonNull(defaultAggregationSelector, "defaultAggregationSelector");
    this.defaultAggregationSelector = defaultAggregationSelector;
    return this;
  }

  /**
   * Ses the retry policy. Retry is disabled by default.
   *
   * @since 1.28.0
   */
  public OtlpHttpMetricExporterBuilder setRetryPolicy(RetryPolicy retryPolicy) {
    requireNonNull(retryPolicy, "retryPolicy");
    delegate.setRetryPolicy(retryPolicy);
    return this;
  }

  OtlpHttpMetricExporterBuilder exportAsJson() {
    delegate.exportAsJson();
    return this;
  }

  /**
   * Constructs a new instance of the exporter based on the builder's values.
   *
   * @return a new exporter's instance
   */
  public OtlpHttpMetricExporter build() {
    return new OtlpHttpMetricExporter(
        delegate, delegate.build(), aggregationTemporalitySelector, defaultAggregationSelector);
  }
}
