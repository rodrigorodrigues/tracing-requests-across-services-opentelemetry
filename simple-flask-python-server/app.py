import logging.config
import os
import sys

from flask import Flask, request
from flask import jsonify, make_response
from flask_zipkin import Zipkin
from prometheus_flask_exporter import PrometheusMetrics
from logstash_async.handler import AsynchronousLogstashHandler
from logstash_async.formatter import FlaskLogstashFormatter

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
    stream=sys.stdout
)

logstash_handler = AsynchronousLogstashHandler(
    app.config.get('LOGSTASH_HOST'),
    app.config.get('LOGSTASH_PORT'),
    database_path=None,
    transport=app.config.get('LOGSTASH_TRANSPORT')
)


class AddTraceHeadersLogstash(FlaskLogstashFormatter):
    def _get_extra_fields(self, record):
        extra_fields = super()._get_extra_fields(record=record, )

        extra_fields['trace_id'] = request.headers.get('X-B3-TraceId')
        extra_fields['span_id'] = request.headers.get('X-B3-SpanId')
        extra_fields['parent_span_id'] = request.headers.get('X-B3-ParentSpanId')
        extra_fields['sample_id'] = request.headers.get('X─B3─Sampled')
        return extra_fields


logstash_handler.formatter = AddTraceHeadersLogstash(metadata={"application_name": "simple-flask-python-service"})
app.logger.addHandler(logstash_handler)

addresses = [{'id': 1, 'street': 'test', 'person_id': 1}, {'id': 2, 'street': 'another street', 'person_id': 3}]


@app.route('/v1/addresses/person/<id>')
def get_all(id):
    trace_id = request.headers.get('X-B3-TraceId')
    app.logger.info(f'FlaskLog - Get address by person_id: {id}\t traceId: {trace_id}')
    log.info(f'Get address by person_id: {id}\t traceId: {trace_id}')
    for i in addresses:
        if i['person_id'] == int(id):
            return make_response(jsonify(addresses[0]), 200)
    return make_response(jsonify(msg=f'Not found address for person_id: {id}'), 404)


app.run(host="0.0.0.0", port=app.config['SERVER_PORT'], debug=True)
