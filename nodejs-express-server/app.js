/*app.js*/
require('./instrumentation.js');
const express = require('express');
const {W3CTraceContextPropagator} = require('@opentelemetry/core');
const {defaultTextMapGetter, defaultTextMapSetter, ROOT_CONTEXT, trace,} = require('@opentelemetry/api');

const parentSpan = trace.getTracer("new").startSpan('main');

// In upstream service
const propagator = new W3CTraceContextPropagator();
let carrier = {};

const PORT = parseInt(process.env.PORT || '8083');
const app = express();

const winston = require("winston");
const logger = winston.createLogger({
    level: "info",
    format: winston.format.json(),
    transports: [
        new winston.transports.Console(),
        new winston.transports.File({ filename: "/tmp/nodejs-express-server.log" }),
    ],
});

app.post('/v1/errors', (req, res) => {
    propagator.extract(
        trace.setSpanContext(ROOT_CONTEXT, parentSpan.spanContext()),
        carrier,
        defaultTextMapGetter
    );
    logger.log("info", `carrier: ${carrier}`); // transport this carrier info to other service via headers or some other

    const parentCtx = propagator.extract(ROOT_CONTEXT, carrier, defaultTextMapGetter);
    logger.log("info", `parentCtx: ${parentCtx}`); // transport this carrier info to other service via headers or some other

    logger.log("info", `Receiving message with headers: ${JSON.stringify(req.headers)}`)
    res.send("Processing errors in nodejs express...");
});

app.listen(PORT, () => {
    console.log(`Listening for requests on http://localhost:${PORT}`);
});
