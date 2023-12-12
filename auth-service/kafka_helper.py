import datetime
import json
import logging
from uuid import uuid4

from confluent_kafka import Consumer, Producer
from confluent_kafka.schema_registry import SchemaRegistryClient
from confluent_kafka.schema_registry.avro import AvroDeserializer, AvroSerializer
from confluent_kafka.serialization import SerializationContext, MessageField, StringSerializer
from opentelemetry import trace
from opentelemetry.trace.propagation.tracecontext import TraceContextTextMapPropagator

from avro.com.example.schema.avro import UpdatePayment, CheckStatus
from avro.com.example.schema.avro.Payment import Payment
from avro.helpers import todict
from instrumentation import instrument_consumer, metrics_provider, trace_provider


def process_messages(payment_schema, update_payment_schema, bootstrap_servers, group_id, consumer_topic, producer_topic, schema_registry_url, service_name):
    sr_conf = {'url': schema_registry_url}
    schema_registry_client = SchemaRegistryClient(sr_conf)

    avro_deserializer = AvroDeserializer(schema_registry_client,
                                         read_schema_file(payment_schema),
                                         todict)

    avro_serializer = AvroSerializer(schema_registry_client,
                                     read_schema_file(update_payment_schema),
                                     todict)

    string_serializer = StringSerializer('utf_8')

    kafka_conf = {'bootstrap.servers': bootstrap_servers,
                     'group.id': group_id,
                     'auto.offset.reset': "latest"}

    tracer_provider = trace_provider(service_name)

    consumer = Consumer(kafka_conf)
    consumer.subscribe([consumer_topic])
    consumer = instrument_consumer(consumer, tracer_provider)
    logging.info(f"consumer: {consumer}")

    producer = Producer(kafka_conf)
    logging.info(f"producer: {producer}")

    while True:
        try:
            # SIGINT can't be handled when polling, limit timeout to 1 second.
            msg = consumer.poll(1.0)
            if msg is None:
                continue

            meter = metrics_provider(service_name).get_meter("auth-service-python-meter")
            histogram = meter.create_histogram("task.duration", "s", "The duration of task execution.")
            start = datetime.datetime.now()
            headers = {}
            for header in msg.headers():
                headers[header[0]] = bytes.decode(header[1])
            logging.info(f'Printing all headers: {headers}')
            carrier = {}
            if "traceparent" in headers:
                carrier = {
                    'TraceId': headers['X-B3-TraceId'],
                    'SpanId': headers['X-B3-SpanId'],
                    'traceparent': headers['traceparent']
                }
            logging.info(f"carrier: {carrier}")

            # Then we use a propagator to get a context from it.
            ctx = TraceContextTextMapPropagator().extract(carrier=carrier)
            logging.info(f"ctx: {ctx}")

            tracer = trace.get_tracer("python-service")
            trace_id = headers.get('X-B3-TraceId', "")
            with tracer.start_as_current_span('consuming-kafka', context=ctx) as span:
                span.set_attribute('message', f"auth-service-python: consuming message for traceID={trace_id}")

            payment_record = avro_deserializer(msg.value(), SerializationContext(msg.topic(), MessageField.VALUE))

            payment_record = Payment(todict(payment_record))

            logging.info(f",traceID={trace_id}\tPayment record: {todict(payment_record)}\n")

            update_payment = UpdatePayment({
                "paymentId": payment_record.get_requestId(),
                "updateAt": datetime.datetime.now(),
                "reasonFailed": "Invalid details provided",
                "status": CheckStatus.AUTH_CHECK
            })

            logging.info(f"update_payment: {todict(update_payment)}")

            producer.produce(topic=producer_topic,
                             key=string_serializer(str(uuid4())),
                             value=avro_serializer(update_payment, SerializationContext(producer_topic, MessageField.VALUE)),
                             on_delivery=delivery_report,
                             headers=msg.headers())

            producer.flush()
            duration = datetime.datetime.now() - start
            histogram.record(duration.total_seconds())
        except KeyboardInterrupt:
            break

    consumer.close()


def delivery_report(err, msg):
    if err is not None:
        logging.info("Delivery failed for User record {}: {}".format(msg.key(), err))
        return
    logging.info('User record {} successfully produced to {} [{}] at offset {}'.format(
        msg.key(), msg.topic(), msg.partition(), msg.offset()))


def read_schema_file(schema):
    with open(f"{schema}") as f:
        schema_str = f.read()
    return schema_str
