import logging

from opentelemetry import metrics
from opentelemetry._logs import set_logger_provider
from opentelemetry.exporter.otlp.proto.grpc._log_exporter import (
    OTLPLogExporter,
)
from opentelemetry.exporter.otlp.proto.grpc.metric_exporter import OTLPMetricExporter
from opentelemetry.exporter.otlp.proto.grpc.trace_exporter import OTLPSpanExporter
from opentelemetry.instrumentation.confluent_kafka import ConfluentKafkaInstrumentor
from opentelemetry.sdk._logs import LoggerProvider, LoggingHandler
from opentelemetry.sdk._logs._internal.export import BatchLogRecordProcessor
from opentelemetry.sdk.metrics import MeterProvider
from opentelemetry.sdk.metrics.export import PeriodicExportingMetricReader
from opentelemetry.sdk.resources import SERVICE_NAME, Resource
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import (
    SimpleSpanProcessor
)


def instrument_consumer(consumer, provider):
    return ConfluentKafkaInstrumentor().instrument_consumer(consumer=consumer, tracer_provider=provider)


def instrument_producer(producer, provider):
    return ConfluentKafkaInstrumentor().instrument_producer(producer=producer, tracer_provider=provider)


def get_resources(service_name):
    return Resource(attributes={
        SERVICE_NAME: service_name
    })


def trace_provider(service_name):
    # Trace Provider
    provider = TracerProvider(resource=get_resources(service_name))
    simple_processor = SimpleSpanProcessor(OTLPSpanExporter())
    provider.add_span_processor(simple_processor)
    return provider


def metrics_provider(service_name):
    # Metrics Provider
    reader = PeriodicExportingMetricReader(OTLPMetricExporter())
    meter_provider = MeterProvider(resource=get_resources(service_name), metric_readers=[reader])
    metrics.set_meter_provider(meter_provider)
    return metrics


def instrument_log(service_name):
    # Log Exporter Provider
    logger_provider = LoggerProvider(resource=get_resources(service_name))
    set_logger_provider(logger_provider)
    exporter = OTLPLogExporter()
    logger_provider.add_log_record_processor(BatchLogRecordProcessor(exporter))
    handler = LoggingHandler(level=logging.DEBUG,logger_provider=logger_provider)
    logging.getLogger().addHandler(handler)
