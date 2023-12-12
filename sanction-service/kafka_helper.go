package main

import (
	"bytes"
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
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"
)

func ProcessMessages(kafkaAddress string, topic string, group string, apiClientErrorUrl string, schemaRegistryUrl string,
	ctx context.Context, serviceName string, otlpUrl string, log *logrus.Logger) {
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
	updateTopic := "update-payment-topic"

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
				//tr := otel.GetTracerProvider().Tracer("sanction-service-golang-trace")
				tr := traceProvider.Tracer("sanction-service-golang-console-trace")
				//log.Info("consoleTracerProvider: %s\n", trConsole)

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
					sendErrorMessage(e, err.Error(), apiClientErrorUrl, log)
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
					sendErrorMessage(e, err.Error(), apiClientErrorUrl, log)
				} else {
					log.Infof("Printing all headers: %v", e.Headers)
					log.Infof(",traceID=%v,spanID=%v,requestId='%v',\tPayment record: %v", traceID.String(), spanID.String(), requestId, value)
					//Simulate Sanction logic
					r := rand.Intn(5)
					time.Sleep(time.Duration(r) * time.Second)
					updatePayment := avro2.UpdatePayment{PaymentId: value.RequestId,
						CheckFailed: false,
						Status:      avro2.CheckStatusSANCTION_CHECK,
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
				sendError(e, apiClientErrorUrl, log)

			default:
				log.Info("Ignored %v", e)
			}
		}
	}

	log.Info("Closing consumer")
	c.Close()

	/*ctx, cancel := signal.NotifyContext(context.Background(), os.Interrupt)
	defer cancel()

	requestStartTime := time.Now()

	if err := startConsumerGroup(ctx, brokerList, topic, group); err != nil {
		log.Fatal(err)
	}

	elapsedTime := float64(time.Since(requestStartTime)) / float64(time.Millisecond)
	requestCount.Add(ctx, 1)
	requestDuration.Record(ctx, elapsedTime)

	<-ctx.Done()*/
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

func sendError(e kafka.Error, apiClientUrl string, log *logrus.Logger) {
	errorMsg := fmt.Sprintf("{\"msg\": \"Processing error: %v\"}", e.String())
	req, err := http.NewRequest("POST", apiClientUrl, bytes.NewBuffer([]byte(errorMsg)))
	if err != nil {
		panic(err)
	}

	req.Header.Add("Content-Type", "application/json")

	client := &http.Client{}
	resp, err := client.Do(req)
	if err != nil {
		log.Info(err)
		return
	}

	log.Info("Response api nodejs-express: ", resp)
}

func sendErrorMessage(e *kafka.Message, error string, apiClientUrl string, log *logrus.Logger) {
	errorMsg := fmt.Sprintf("{\"msg\": \"Processing error: %v\"}", error)
	req, err := http.NewRequest("POST", apiClientUrl, bytes.NewBuffer([]byte(errorMsg)))
	if err != nil {
		panic(err)
	}

	for _, h := range e.Headers {
		req.Header.Add(h.Key, string(e.Value))
	}
	req.Header.Add("Content-Type", "application/json")

	client := &http.Client{}
	resp, err := client.Do(req)
	if err != nil {
		panic(err)
	}

	log.Info("Response api nodejs-express: ", resp)

}

/*func startConsumerGroup(ctx context.Context, brokerList []string, topic string, group string) error {
	consumerGroupHandler := Consumer{}
	// Wrap instrumentation
	handler := otelsarama.WrapConsumerGroupHandler(&consumerGroupHandler)

	config := sarama.NewConfig()
	config.Version = sarama.V2_5_0_0
	config.Consumer.Offsets.Initial = sarama.OffsetOldest

	// Create consumer group
	consumerGroup, err := sarama.NewConsumerGroup(brokerList, group, config)
	if err != nil {
		return fmt.Errorf("starting consumer group: %w", err)
	}

	err = consumerGroup.Consume(ctx, []string{topic}, handler)
	if err != nil {
		return fmt.Errorf("consuming via handler: %w", err)
	}
	return nil
}

func processMessage(msg *sarama.ConsumerMessage) {
	// Extract tracing info from message
	headers := make(map[string]string)
	for _, v := range msg.Headers {
		headers[string(v.Key)] = string(v.Value)
	}

	ctx := otel.GetTextMapPropagator().Extract(context.Background(), otelsarama.NewConsumerMessageCarrier(msg))

	tr := otel.Tracer("consumer")
	traceId := headers["X-B3-TraceId"]
	_, span := tr.Start(ctx, "consuming message for traceID="+traceId, trace.WithAttributes(
		semconv.MessagingOperationProcess,
	))
	defer span.End()

	//roll := 1 + rand.Intn(6)

	//rollValueAttr := attribute.Int("roll.value", roll)
	//span.SetAttributes(rollValueAttr)
	//rollCnt.Add(ctx, 1, metric.WithAttributes(rollValueAttr))
	var payment Payment
	if err := json.Unmarshal(msg.Value, &payment); err != nil {
		fmt.Info(err)
		return
	}
	log.Info("Successful read message for requestId: ", payment.RequestID)
	log.Info("Successful read message:headers: ", headers)

	errorMsg := fmt.Sprintf("{\"msg\": \"Processing error with traceId: %v\"}", traceId)
	req, err := http.NewRequest("POST", apiClientUrl, bytes.NewBuffer([]byte(errorMsg)))
	if err != nil {
		panic(err)
	}

	for k, v := range headers {
		req.Header.Add(k, v)
	}
	req.Header.Add("Content-Type", "application/json")

	client := &http.Client{}
	resp, err := client.Do(req)
	if err != nil {
		fmt.Info(err)
		return
	}

	log.Info("Response api nodejs-express: ", resp)
}
*/
// Consumer represents a Sarama consumer group consumer.
//type Consumer struct{}

/*// Setup is run at the beginning of a new session, before ConsumeClaim.
func (consumer *Consumer) Setup(sarama.ConsumerGroupSession) error {
	return nil
}

// Cleanup is run at the end of a session, once all ConsumeClaim goroutines have exited.
func (consumer *Consumer) Cleanup(sarama.ConsumerGroupSession) error {
	return nil
}

// ConsumeClaim must start a consumer loop of ConsumerGroupClaim's Messages().
func (consumer *Consumer) ConsumeClaim(session sarama.ConsumerGroupSession, claim sarama.ConsumerGroupClaim) error {
	/*	var err error
		rollCntInit, err := meter.Int64Counter("dice.rolls",
			metric.WithDescription("The number of rolls by roll value"),
			metric.WithUnit("{roll}"))

		if err != nil {
			return err
		}
		rollCnt = rollCntInit
*/
// NOTE:
// Do not move the code below to a goroutine.
// The `ConsumeClaim` itself is called within a goroutine, see:
// https://github.com/IBM/sarama/blob/master/consumer_group.go#L27-L29
/*	for message := range claim.Messages() {
		processMessage(message)
		session.MarkMessage(message, "")
	}

	return nil
}*/
