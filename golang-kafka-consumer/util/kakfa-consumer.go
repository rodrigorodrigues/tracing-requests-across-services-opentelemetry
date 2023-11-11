package util

import (
	"bytes"
	"context"
	"fmt"
	"github.com/IBM/sarama"
	"github.com/dnwe/otelsarama"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/metric"
	sdk "go.opentelemetry.io/otel/sdk/metric"
	semconv "go.opentelemetry.io/otel/semconv/v1.17.0"
	"go.opentelemetry.io/otel/trace"
	"log"
	"net/http"
	"os"
	"os/signal"
	"strings"
	"time"
)

var (
	//tracer                  = otel.Tracer("readKafka")
	//meter                   = otel.Meter("readKafka")
	//requestCountInternal    metric.Int64Counter
	//requestDurationInternal metric.Float64Histogram
	apiClientUrl = ""
)

func ReadKafkaMessages(kafkaAddress string, topic string, group string, apiClient string, meterProvider *sdk.MeterProvider) {
	// Create an instance on a meter for the given instrumentation scope
	meter := meterProvider.Meter(
		"github.com/.../example/manual-instrumentation",
		metric.WithInstrumentationVersion("v0.0.0"),
	)

	// Create two synchronous instruments: counter and histogram
	requestCount, err := meter.Int64Counter(
		"request_count",
		metric.WithDescription("Incoming request count"),
		metric.WithUnit("request"),
	)
	if err != nil {
		log.Fatalln(err)
	}
	//requestCountInternal = requestCount
	requestDuration, err := meter.Float64Histogram(
		"duration",
		metric.WithDescription("Incoming end to end duration"),
		metric.WithUnit("{call}"),
	)
	//requestDurationInternal = requestDuration
	if err != nil {
		log.Fatalln(err)
	}

	apiClientUrl = apiClient
	brokerList := strings.Split(kafkaAddress, ",")
	log.Printf("Kafka brokers: %s", strings.Join(brokerList, ", "))

	ctx, cancel := signal.NotifyContext(context.Background(), os.Interrupt)
	defer cancel()

	requestStartTime := time.Now()

	if err := startConsumerGroup(ctx, brokerList, topic, group); err != nil {
		log.Fatal(err)
	}

	elapsedTime := float64(time.Since(requestStartTime)) / float64(time.Millisecond)
	requestCount.Add(ctx, 1)
	requestDuration.Record(ctx, elapsedTime)

	<-ctx.Done()
}

func startConsumerGroup(ctx context.Context, brokerList []string, topic string, group string) error {
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

func printMessage(msg *sarama.ConsumerMessage) {
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
	log.Println("Successful read message: ", string(msg.Value))
	log.Println("Successful read message:headers: ", headers)

	req, err := http.NewRequest("POST", apiClientUrl, bytes.NewBuffer([]byte(fmt.Sprintf("{\"msg\": \"Processing error with traceId: %v\"}", traceId))))
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
		fmt.Println(err)
		return
	}

	log.Println("Response api nodejs-express: ", resp)
}

// Consumer represents a Sarama consumer group consumer.
type Consumer struct{}

// Setup is run at the beginning of a new session, before ConsumeClaim.
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
	for message := range claim.Messages() {
		printMessage(message)
		session.MarkMessage(message, "")
	}

	return nil
}
