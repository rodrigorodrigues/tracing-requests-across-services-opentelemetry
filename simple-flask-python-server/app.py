import logging.config
import os
import sys
import requests
import textwrap

from flask import has_request_context, request
from flask.logging import default_handler
from flask import Flask, request
from flask import jsonify, make_response
from flask_zipkin import Zipkin
from logstash_async.formatter import FlaskLogstashFormatter
from logstash_async.handler import AsynchronousLogstashHandler
from prometheus_flask_exporter import PrometheusMetrics
from opentelemetry.instrumentation.flask import FlaskInstrumentor


##########################
# OpenTelemetry Settings #
##########################
from opentelemetry.sdk.resources import Resource
import uuid

OTEL_RESOURCE_ATTRIBUTES = {
    "service.instance.id": str(uuid.uuid1()),
    "environment": "local"
}

##########
# Traces #
##########
from opentelemetry import trace
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor
from opentelemetry.exporter.otlp.proto.grpc.trace_exporter import OTLPSpanExporter
from opentelemetry.trace.status import Status, StatusCode

# Initialize tracing and an exporter that can send data to an OTLP endpoint
# SELECT * FROM Span WHERE instrumentation.provider='opentelemetry'
trace.set_tracer_provider(TracerProvider(resource=Resource.create(OTEL_RESOURCE_ATTRIBUTES)))
trace.get_tracer_provider().add_span_processor(BatchSpanProcessor(OTLPSpanExporter()))


###########
# Metrics #
###########
from opentelemetry import metrics
from opentelemetry.sdk.metrics import MeterProvider
from opentelemetry.sdk.metrics.export import PeriodicExportingMetricReader
from opentelemetry.exporter.otlp.proto.grpc.metric_exporter import OTLPMetricExporter

# Initialize metering and an exporter that can send data to an OTLP endpoint
# SELECT count(`http.server.active_requests`) FROM Metric FACET `service.name` TIMESERIES
metrics.set_meter_provider(MeterProvider(resource=Resource.create(OTEL_RESOURCE_ATTRIBUTES), metric_readers=[PeriodicExportingMetricReader(OTLPMetricExporter())]))
metrics.get_meter_provider()
fib_counter = metrics.get_meter("opentelemetry.instrumentation.custom").create_counter("fibonacci.invocations", unit="1", description="Measures the number of times the fibonacci method is invoked.")


########
# Logs # - OpenTelemetry Logs are still in the experimental state, so function names may change in the future
########

from opentelemetry import _logs
from opentelemetry.sdk._logs import LoggerProvider, LoggingHandler
from opentelemetry.sdk._logs.export import BatchLogRecordProcessor
from opentelemetry.exporter.otlp.proto.grpc._log_exporter import OTLPLogExporter

# Initialize logging and an exporter that can send data to an OTLP endpoint by attaching OTLP handler to root logger
# SELECT * FROM Log WHERE instrumentation.provider='opentelemetry'
_logs.set_logger_provider(LoggerProvider(resource=Resource.create(OTEL_RESOURCE_ATTRIBUTES)))
logging.getLogger().addHandler(LoggingHandler(logger_provider=_logs.get_logger_provider().add_log_record_processor(BatchLogRecordProcessor(OTLPLogExporter()))))


app = Flask(__name__)
if not os.getenv('ENV_FILE_LOCATION'):
    os.environ["ENV_FILE_LOCATION"] = ".env"
app.config.from_envvar('ENV_FILE_LOCATION')

zipkin = Zipkin(app, sample_rate=100)
metrics = PrometheusMetrics(app)
# static information as metric
metrics.info('app_info', 'Application info', version='1.0.0')
log = logging.getLogger(__name__)
logging.basicConfig(
    format="%(levelname)s [%(name)s %(funcName)s] %(message)s",
    level='INFO',
    stream=sys.stdout,
    filename="/tmp/simple-flask-python-server.log"
)


class RequestFormatter(logging.Formatter):
    def format(self, record):
        if has_request_context():
            record.url = request.url
            record.remote_addr = request.remote_addr
            record.traceId = request.headers.get('X-B3-TraceId')
            record.spanId = request.headers.get('X-B3-SpanId')
        else:
            record.url = ""
            record.remote_addr = ""
            record.traceId = ""
            record.spanId = ""

        return super().format(record)


formatter = RequestFormatter(
    '%(asctime)s %(remote_addr)s %(levelname)s [%(module)s,%(url)s'
    ',traceID=%(traceId)s,%(spanId)s]'
)
default_handler.setFormatter(formatter)
root = logging.getLogger()
root.addHandler(default_handler)
file_handler = logging.FileHandler(filename="/tmp/simple-flask-python-server.log")
file_handler.setFormatter(formatter)
root.addHandler(file_handler)


# logstash_handler = AsynchronousLogstashHandler(
#     app.config.get('LOGSTASH_HOST'),
#     app.config.get('LOGSTASH_PORT'),
#     database_path=None,
#     transport=app.config.get('LOGSTASH_TRANSPORT')
# )

FlaskInstrumentor().instrument_app(app)


class AddTraceHeadersLogstash(FlaskLogstashFormatter):
    def _get_extra_fields(self, record):
        extra_fields = super()._get_extra_fields(record=record, )

        extra_fields['trace_id'] = request.headers.get('X-B3-TraceId')
        extra_fields['span_id'] = request.headers.get('X-B3-SpanId')
        extra_fields['parent_span_id'] = request.headers.get('X-B3-ParentSpanId')
        extra_fields['sample_id'] = request.headers.get('X─B3─Sampled')
        return extra_fields


# logstash_handler.formatter = AddTraceHeadersLogstash(metadata={"application_name": "simple-flask-python-service"})
# app.logger.addHandler(logstash_handler)

addresses = [{'id': 1, 'street': 'test', 'person_id': 1}, {'id': 2, 'street': 'another street', 'person_id': 3}]


@app.route('/v1/addresses/person/<id>')
def get_all(id):
    # trace_id = request.headers.get('X-B3-TraceId')
    app.logger.info(f'Print all headers: {request.headers}')
    log.info(f'Get address by person_id: {id}')
    # log.info(f'Get address by person_id: {id}\t traceID={trace_id}')
    for i in addresses:
        if i['person_id'] == int(id):
            return make_response(jsonify(addresses[0]), 200)
    return make_response(jsonify(msg=f'Not found address for person_id: {id}'), 404)


app.run(host="0.0.0.0", port=app.config['SERVER_PORT'], debug=True)
