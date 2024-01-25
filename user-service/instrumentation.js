/*instrumentation.js*/
const opentelemetry = require('@opentelemetry/sdk-node');
const { Resource } = require('@opentelemetry/resources');
const {
    getNodeAutoInstrumentations,
} = require('@opentelemetry/auto-instrumentations-node');
const {
    OTLPTraceExporter,
} = require('@opentelemetry/exporter-trace-otlp-grpc');
const {
    OTLPMetricExporter,
} = require('@opentelemetry/exporter-metrics-otlp-grpc');
const { PeriodicExportingMetricReader } = require('@opentelemetry/sdk-metrics');
const {SemanticResourceAttributes} = require("@opentelemetry/semantic-conventions");
const {
    CompositePropagator,
    W3CBaggagePropagator,
    W3CTraceContextPropagator,
} = require("@opentelemetry/core");
// For troubleshooting, set the log level to DiagLogLevel.DEBUG
const {
    diag,
    DiagConsoleLogger,
    DiagLogLevel
} = require("@opentelemetry/api");
// const {registerInstrumentations} = require('@opentelemetry/instrumentation');
const {WinstonInstrumentation} = require('@opentelemetry/instrumentation-winston');

const otlpDebug = `${process.env.OTLP_DEBUG_LEVEL || true}`;
if (otlpDebug === "true") {
    diag.setLogger(new DiagConsoleLogger(), DiagLogLevel.INFO);
}

const resource = Resource.default().merge(
    new Resource({
        [SemanticResourceAttributes.SERVICE_NAME]: 'user-service-nodejs',
        [SemanticResourceAttributes.SERVICE_VERSION]: '0.1.0',
    }),
);

const url = `${process.env.OTLP_URL || 'http://localhost:4317'}`;
const sdk = new opentelemetry.NodeSDK({
    resource: resource,
    traceExporter: new OTLPTraceExporter({
        // optional - default url is http://localhost:4318/v1/traces
        url: url,
        // optional - collection of custom headers to be sent with each request, empty by default
        headers: {},
    }),
    textMapPropagator: new CompositePropagator({
        propagators: [new W3CBaggagePropagator(), new W3CTraceContextPropagator()],
    }),
    metricReader: new PeriodicExportingMetricReader({
        exporter: new OTLPMetricExporter({
            url: url, // url is optional and can be omitted - default is http://localhost:4317/v1/metrics
            headers: {}, // an optional object containing custom headers to be sent with each request
            concurrencyLimit: 1, // an optional limit on pending requests
        }),
    }),
    instrumentations: [
        getNodeAutoInstrumentations(),
    ],
    autoDetectResources: true
});
sdk.start();
