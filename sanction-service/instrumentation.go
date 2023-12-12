package main

import (
	"context"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/exporters/otlp/otlpmetric/otlpmetricgrpc"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracegrpc"
	"go.opentelemetry.io/otel/propagation"
	"go.opentelemetry.io/otel/sdk/metric"
	"go.opentelemetry.io/otel/sdk/resource"
	"go.opentelemetry.io/otel/sdk/trace"
)

func newResource(serviceName string) *resource.Resource {
	resources, err := resource.New(
		context.Background(),
		resource.WithAttributes(
			attribute.String("service.name", serviceName),
			attribute.String("service.version", "1.0.0"),
			attribute.String("library.language", "go"),
		),
	)
	if err != nil {
		log.Printf("Could not set resources: %v", err)
	}
	return resources
}

func InitTraceProvider(serviceName string, collectorURL string) (*trace.TracerProvider, error) {

	ctx := context.Background()
	exporter, err := otlptracegrpc.New(
		ctx,
		otlptracegrpc.WithInsecure(),
		otlptracegrpc.WithEndpoint(collectorURL),
	)

	if err != nil {
		return nil, err
	}

	resources := newResource(serviceName)

	batchSpanProcessor := trace.NewBatchSpanProcessor(exporter)
	traceProvider := trace.NewTracerProvider(
		trace.WithSpanProcessor(batchSpanProcessor),
		trace.WithSampler(trace.AlwaysSample()),
		trace.WithBatcher(exporter),
		trace.WithResource(resources),
	)
	otel.SetTracerProvider(traceProvider)
	otel.SetTextMapPropagator(propagation.NewCompositeTextMapPropagator(propagation.TraceContext{}))
	return traceProvider, err
}

func InitMeterProvider(serviceName string, collectorURL string) (*metric.MeterProvider, error) {
	secureOption := otlpmetricgrpc.WithInsecure()
	// Instantiate the OTLP HTTP exporter
	exporter, err := otlpmetricgrpc.New(
		context.Background(),
		secureOption,
		otlpmetricgrpc.WithEndpoint(collectorURL),
	)

	if err != nil {
		return nil, err
	}

	resources := newResource(serviceName)

	meterProvider := metric.NewMeterProvider(
		metric.WithResource(resources),
		metric.WithReader(metric.NewPeriodicReader(exporter)),
	)
	otel.SetMeterProvider(meterProvider)
	return meterProvider, err
}
