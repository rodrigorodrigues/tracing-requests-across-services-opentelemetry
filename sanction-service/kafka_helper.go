package main

import (
	"context"
	"fmt"
	"github.com/confluentinc/confluent-kafka-go/kafka"
	"github.com/confluentinc/confluent-kafka-go/schemaregistry"
	"github.com/confluentinc/confluent-kafka-go/schemaregistry/serde"
	"github.com/confluentinc/confluent-kafka-go/schemaregistry/serde/avro"
	"github.com/etf1/opentelemetry-go-contrib/instrumentation/github.com/confluentinc/confluent-kafka-go/otelconfluent"
	"github.com/sirupsen/logrus"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/metric"
	"go.opentelemetry.io/otel/propagation"
	"go.opentelemetry.io/otel/trace"
	avro2 "golang-kafka-consumer/avro"
	"math/rand"
	"os"
	"os/signal"
	"strings"
	"syscall"
	"time"
)

func ProcessMessages(kafkaAddress string, topic string, updateTopic string,
	group string, schemaRegistryUrl string,
	ctx context.Context, serviceName string, otlpUrl string, log *logrus.Logger,
	sanctionNames []string) {
	traceProvider, err := InitTraceProvider(serviceName, otlpUrl)

	if err != nil {
		log.Fatalln(os.Stderr, "Failed to setup OTEL traceProvider: %s", err)
		os.Exit(1)
	}

	defer func() {
		if err := traceProvider.Shutdown(ctx); err != nil {
			log.Fatal("failed to shutdown TracerProvider: %w", err)
		}
	}()

	meterProvider, err := InitMeterProvider(serviceName, otlpUrl)

	if err != nil {
		log.Fatalln(os.Stderr, "Failed to setup OTEL meterProvider: %s", err)
		os.Exit(1)
	}

	meter := meterProvider.Meter("sanction-service-golang-meter")
	histogram, _ := meter.Float64Histogram(
		"task.duration",
		metric.WithDescription("The duration of task execution."),
		metric.WithUnit("s"),
	)

	sigchan := make(chan os.Signal, 1)
	signal.Notify(sigchan, syscall.SIGINT, syscall.SIGTERM)

	c, err := kafka.NewConsumer(&kafka.ConfigMap{
		"bootstrap.servers":  kafkaAddress,
		"group.id":           group,
		"session.timeout.ms": 6000,
		"auto.offset.reset":  "latest"})

	if err != nil {
		log.Fatalln(os.Stderr, "Failed to create consumer: %s", err)
		os.Exit(1)
	}

	p, err := kafka.NewProducer(&kafka.ConfigMap{
		"bootstrap.servers":  kafkaAddress,
		"session.timeout.ms": 6000})

	if err != nil {
		log.Fatalln(os.Stderr, "Failed to create consumer: %s", err)
		os.Exit(1)
	}

	provider := otelconfluent.WithTracerProvider(traceProvider)

	producer := otelconfluent.NewProducerWithTracing(p, provider)

	log.Infof("Created producer %v", producer)

	consumer := otelconfluent.NewConsumerWithTracing(c, provider)

	log.Infof("Created Consumer %v", consumer)

	defer func() { _ = consumer.Close() }()

	client, err := schemaregistry.NewClient(schemaregistry.NewConfig(schemaRegistryUrl))

	if err != nil {
		log.Fatalln("Failed to create schema registry client: %s", err)
		os.Exit(1)
	}

	deser, err := avro.NewSpecificDeserializer(client, serde.ValueSerde, avro.NewDeserializerConfig())

	if err != nil {
		log.Fatalln("Failed to create deserializer: %s", err)
		os.Exit(1)
	}

	ser, err := avro.NewSpecificSerializer(client, serde.ValueSerde, avro.NewSerializerConfig())

	if err != nil {
		fmt.Printf("Failed to create serializer: %s\n", err)
		os.Exit(1)
	}

	err = consumer.SubscribeTopics([]string{topic}, nil)

	run := true

loop:
	for run {
		select {
		case sig := <-sigchan:
			log.Info("Caught signal %v: terminating", sig)
			run = false
		default:
			// Optional delivery channel, if not specified the Producer object's
			// .Events channel is used.
			deliveryChan := make(chan kafka.Event)
			ev := consumer.Poll(100)
			if ev == nil {
				continue
			}

			switch e := ev.(type) {
			case *kafka.Message:
				start := time.Now()
				tr := traceProvider.Tracer("sanction-service-golang-console-trace")

				// Extract tracing info from message
				headers := make(map[string]string)
				for _, v := range e.Headers {
					headers[v.Key] = string(v.Value)
				}
				headers["traceparent"] = headers["b3"]
				log.Infof("headers: %s\n", headers)

				carrier := propagation.MapCarrier(headers)
				log.Infof("carrier: %s\n", carrier)
				background := context.Background()
				ctx := otel.GetTextMapPropagator().Extract(background, carrier)
				traceID, spanID, requestId, err := parseHeaders(headers)
				if err != nil {
					log.Fatalln(fmt.Fprintf(os.Stderr, "%% Error: %v", err.Error()))
					break loop
				}

				log.Infof("traceID=%s\tspanID=%s", traceID, spanID)
				ctx = trace.ContextWithRemoteSpanContext(background,
					trace.NewSpanContext(trace.SpanContextConfig{TraceID: *traceID, SpanID: *spanID}))
				ctx, span := tr.Start(ctx, "sanction-service-golang-span", trace.WithAttributes(
					attribute.String("message", "sanction-service-golang consuming message for traceID="+traceID.String()),
				))

				value := avro2.Payment{}
				err = deser.DeserializeInto(*e.TopicPartition.Topic, e.Value, &value)
				if err != nil {
					log.Infof("Failed to deserialize payload: %s", err)
				} else {
					log.Infof("Printing all headers: %v", e.Headers)
					log.Infof(",traceID=%v,spanID=%v,requestId='%v',\tPayment record: %v", traceID.String(), spanID.String(), requestId, value)
					checkFailed := sanctionNameCheckFailed(sanctionNames, value)
					reasonFailed := avro2.UnionNullString{
						Null:      nil,
						String:    "",
						UnionType: 1,
					}
					if checkFailed {
						reasonFailed.String = "Sanction Check has Failed"
					}
					updatePayment := avro2.UpdatePayment{
						RequestId:    value.RequestId,
						CheckFailed:  checkFailed,
						Status:       avro2.CheckStatusSANCTION_CHECK,
						ReasonFailed: &reasonFailed,
					}

					payload, err := ser.Serialize(updateTopic, &updatePayment)
					if err != nil {
						log.Fatalf("Failed to serialize payload: %s\n", err)
						os.Exit(1)
					}

					err = p.Produce(&kafka.Message{
						TopicPartition: kafka.TopicPartition{Topic: &updateTopic, Partition: kafka.PartitionAny},
						Value:          payload,
						Headers:        e.Headers,
					}, deliveryChan)
					if err != nil {
						log.Fatalf("Produce failed: %v\n", err)
						os.Exit(1)
					}

					e := <-deliveryChan
					m := e.(*kafka.Message)

					if m.TopicPartition.Error != nil {
						log.Printf("Delivery failed: %v\n", m.TopicPartition.Error)
					} else {
						log.Printf("Delivered message to topic %s [%d] at offset %v\n",
							*m.TopicPartition.Topic, m.TopicPartition.Partition, m.TopicPartition.Offset)
					}

				}
				defer span.End()
				duration := time.Since(start)
				histogram.Record(ctx, duration.Seconds())
			case kafka.Error:
				// Errors should generally be considered
				// informational, the client will try to
				// automatically recover.
				log.Fatalln(fmt.Fprintf(os.Stderr, "%% Error: %v: %v", e.Code(), e))
			default:
				log.Info("Ignored %v", e)
			}
		}
	}

	log.Info("Closing consumer")
	c.Close()
}

// TODO Simulating Sanction logic - in the real world should use something like https://github.com/moov-io/watchman
func sanctionNameCheckFailed(sanctionNames []string, value avro2.Payment) bool {
	r := rand.Intn(10)
	time.Sleep(time.Duration(r) * time.Second)
	for _, name := range sanctionNames {
		if strings.EqualFold(name, value.UsernameFrom) ||
			strings.EqualFold(name, value.UsernameTo) ||
			strings.EqualFold(name, value.UsernameFromAddress) ||
			strings.EqualFold(name, value.UsernameToAddress) {
			return true
		}
	}
	return false
}

func parseHeaders(headers map[string]string) (*trace.TraceID, *trace.SpanID, *string, error) {
	var traceIDResponse trace.TraceID
	var spanIDResponse trace.SpanID
	var requestId string
	for k, v := range headers {
		if k == "X-B3-TraceId" {
			traceID, err := trace.TraceIDFromHex(v)
			if err != nil {
				log.Fatalln("Invalid traceId header")
				return nil, nil, nil, err
			}
			traceIDResponse = traceID
		} else if k == "X-B3-SpanId" {
			spanID, err := trace.SpanIDFromHex(v)
			if err != nil {
				log.Fatalln("Invalid spanId header")
				return nil, nil, nil, err
			}
			spanIDResponse = spanID
		} else if k == "requestId" {
			requestId = v
		}
		if traceIDResponse.IsValid() && spanIDResponse.IsValid() && requestId != "" {
			break
		}
	}
	return &traceIDResponse, &spanIDResponse, &requestId, nil
}
