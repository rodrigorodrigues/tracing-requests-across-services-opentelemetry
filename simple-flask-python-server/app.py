import logging.config
import os
import sys
import uuid

from flask import Flask, request
from flask import has_request_context
from flask import jsonify, make_response
from flask.logging import default_handler
from opentelemetry.instrumentation.flask import FlaskInstrumentor
##########################
# OpenTelemetry Settings #
##########################
from opentelemetry.sdk.resources import Resource

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
root.addHandler(file_handler)


FlaskInstrumentor().instrument_app(app)

addresses = [{'id': 1, 'street': 'test', 'person_id': 1}, {'id': 2, 'street': 'another street', 'person_id': 3}]


@app.route('/v1/addresses/person/<id>')
def get_all(id):
    app.logger.info(f'Print all headers: {request.headers}')
    trace_id = request.headers.get('X-B3-TraceId')
    app.logger.info(f'Get address by person_id: {id}\t traceID={trace_id}')
    for i in addresses:
        if i['person_id'] == int(id):
            return make_response(jsonify(addresses[0]), 200)
    return make_response(jsonify(msg=f'Not found address for person_id: {id}'), 404)


app.run(host="0.0.0.0", port=app.config['SERVER_PORT'], debug=True)
