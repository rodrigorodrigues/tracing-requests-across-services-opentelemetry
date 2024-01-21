require('./instrumentation.js');

const {defaultTextMapGetter, metrics, context, trace, propagation,} = require('@opentelemetry/api');

const winston = require("winston");
const { createLogger, format, transports } = require('winston');
const {MetricOptions} = require("@opentelemetry/api/build/src/metrics/Metric");
const { combine, timestamp, label, printf } = format;

const myFormat = printf(({ level, message, label, timestamp }) => {
    return `${timestamp} ${level.toUpperCase()} serviceName='${label}' : ${message}`;
});

const logger = winston.createLogger({
    level: "info",
    format: combine(
        label({ label: 'user-service-nodejs' }),
        timestamp(),
        myFormat
    ),
    transports: [
        new winston.transports.Console(),
        new winston.transports.File({ filename: "/tmp/user-service-nodejs.log" }),
    ],
});

const KafkaAvro = require('kafkajs-avro').KafkaAvro;

(async () => {
    let config = {
        clientId: "test",
        brokers: [`${process.env.BOOSTRAP_SERVERS || 'localhost:9092'}`],
        logLevel: 'TRACE',
        avro: {
            url: `${process.env.SCHEMA_REGISTRY_URL || 'http://localhost:8081'}`
        }
    };
    const kafka = new KafkaAvro(config)
    logger.log("info", `kafkaConfigConsumer: ${JSON.stringify(config)}`);

    /* Consumer
       The consumer does not require any extra settings to be built.
       You may just remove .avro from the next line and you will see raw messages from
       the brokers - without avro decoding.
    */
    const consumer = kafka.avro.consumer({ groupId: `${process.env.GROUP_ID || 'nodejs-server'}` })
    await consumer.connect()
    await consumer.subscribe({ topic: `${process.env.PAYMENT_TOPIC || 'payment-topic'}` })

    const producer = kafka.avro.producer()
    await producer.connect()

    logger.info("Starting Kafka consuming messages");
    const updateTopic = `${process.env.UPDATE_PAYMENT_TOPIC || 'update-payment-topic'}`;

    await consumer.run({
        eachMessage: async ({topic, partition, message}) => {
            const meter = metrics.getMeter("user-service-nodejs-task-meter");
            let histogram = meter.createHistogram("task.duration", {
                description: "The duration of task execution.",
                unit: "s"
            });
            const startTime = new Date().getTime();
            let headers = [];
            for (const [key, value] of Object.entries(message.headers)) {
                headers[key] = value.toString();
            }
            logger.log("info", `Printing all headers: ${JSON.stringify(headers)}`);
            const carrier = {
                'TraceId': headers['X-B3-TraceId'].toString(),
                'SpanId': headers['X-B3-SpanId'].toString(),
                'traceparent': headers['traceparent'].toString(),
                'tracestate': headers['b3'].toString()
            };
            logger.log("info", `carrier: ${JSON.stringify(carrier)}`);

            const ctx = propagation.extract(context.active(), carrier, defaultTextMapGetter);

            let tracer = trace.getTracer('user-service-tracer');

            let span = tracer.startSpan(
                'user-service-span',
                {
                    attributes: {'message': `user-service-nodejs: consuming message for traceID=${message.headers['X-B3-TraceId']}`}
                },
                ctx,
            );

            logger.log("info", `traceID=${message.headers['X-B3-TraceId']}\tspanID=${message.headers['X-B3-SpanId']}\tPayment record: ${JSON.stringify(message.value)}`);

            let checkFailed = false;
            let reasonFailed = null;

            if (message.value.usernameFrom === message.value.usernameTo) {
                checkFailed = true;
                reasonFailed = "Invalid Check- Cannot make a payment for yourself!";
            }

            let updatePayment = {
                reasonFailed: reasonFailed,
                requestId: message.value.requestId,
                status: "USER_CONFIRMATION_CHECK",
                updateAt: Date.now(),
                checkFailed: checkFailed
            };

            logger.log("info", `Updating payment: ${JSON.stringify(updatePayment)}`);

            await producer.send({
                topic: updateTopic,
                 messages: [{
                     subject: "update-payment-topic-value",
                     version: "latest",
                     value: updatePayment,
                     headers: headers
                 }]
            })

            // Set the created span as active in the deserialized context.
            trace.setSpan(ctx, span);
            span.end();
            const endTime = new Date().getTime();
            const duration = (endTime - startTime) / 1000;

            // Record the duration of the task operation
            histogram.record(duration);
        }
    })
})()