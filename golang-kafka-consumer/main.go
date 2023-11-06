/**
 * Copyright 2016 Confluent Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Example function-based high-level Apache Kafka consumer
package main

// consumer_example implements a consumer using the non-channel Poll() API
// to retrieve messages and events.

import (
	"context"
	"fmt"
	"github.com/joho/godotenv"
	"go.opentelemetry.io/otel/trace"
	"kafka-go-getting-started/util"
	"log"
	"os"
	"strconv"
)

var loadEnvFlag = true

func GetEnv(key string) string {
	if loadEnvFlag {
		loadEnv()
		loadEnvFlag = false
	}
	value := os.Getenv(key)
	if "" == value {
		valueEnv, exists := os.LookupEnv(key)
		if !exists {
			panic("Not found variable: " + key)
		}
		value = valueEnv
	}
	log.Print(fmt.Sprintf("Env = %+v\tvalue = %v\n", key, value))
	return value
}

func GetEnvAsBool(key string) bool {
	value, err := strconv.ParseBool(GetEnv(key))
	if err != nil {
		return false
	}
	log.Print(fmt.Sprintf("Env = %+v\tvalue = %v\n", key, value))
	return value
}

func loadEnv() {
	env := ".env"
	environment := os.Getenv("ENVIRONMENT")
	if environment != "" {
		env += "." + environment
	}

	log.Print(fmt.Sprintf("Env = %+v\n", env))
	if err := godotenv.Load(env); err != nil {
		panic(err)
	}
}

func openLogFile(path string) (*os.File, error) {
	logFile, err := os.OpenFile(path, os.O_WRONLY|os.O_APPEND|os.O_CREATE, 0644)
	if err != nil {
		return nil, err
	}
	return logFile, nil
}

var tracer trace.Tracer

func main() {
	file, err := openLogFile(GetEnv("LOG_FILE"))
	if err != nil {
		log.Fatal(err)
	}
	log.SetOutput(file)
	log.SetFlags(log.LstdFlags | log.Lshortfile | log.Lmicroseconds)

	//Start Otel
	//ctx := context.Background()

	service := GetEnv("SERVICE_NAME")
	tracerProvider := util.InitTracer(service, GetEnv("OTEL_EXPORTER_OTLP_ENDPOINT"), GetEnvAsBool("INSECURE_MODE"))
	defer tracerProvider(context.Background())

	meterProvider := util.MeterProvider(service)
	defer meterProvider(context.Background())

	/*exp, err := util.NewExporter(ctx)
	if err != nil {
		log.Fatalf("failed to initialize exporter: %v", err)
	}

	// Create a new tracer provider with a batch span processor and the given exporter.
	tp := util.NewTraceProvider(exp)

	// Handle shutdown properly so nothing leaks.
	defer func() { _ = tp.Shutdown(ctx) }()

	otel.SetTracerProvider(tp)

	// Finally, set the tracer that can be used for this package.
	tracer = tp.Tracer("GoClientKafkaConsumerService")
	ctxTodo := context.TODO()
	ctxTodo, parentSpan := tracer.Start(ctxTodo, "parent")
	defer parentSpan.End()
	ctxTodo, childSpan := tracer.Start(ctxTodo, "child")
	defer childSpan.End()*/
	//End Otel

	bootstrapServers := GetEnv("KAFKA_BOOTSTRAP_SERVERS")
	group := GetEnv("KAFKA_GROUP")
	topics := GetEnv("KAFKA_TOPIC")

	util.ReadKafkaMessages(bootstrapServers, topics, group)
	/*sigchan := make(chan os.Signal, 1)
	signal.Notify(sigchan, syscall.SIGINT, syscall.SIGTERM)

	c, err := kafka.NewConsumer(&kafka.ConfigMap{
		"bootstrap.servers": bootstrapServers,
		// Avoid connecting to IPv6 brokers:
		// This is needed for the ErrAllBrokersDown show-case below
		// when using localhost brokers on OSX, since the OSX resolver
		// will return the IPv6 addresses first.
		// You typically don't need to specify this configuration property.
		"broker.address.family": "v4",
		"group.id":              group,
		"session.timeout.ms":    6000,
		// Start reading from the first message of each assigned
		// partition if there are no previously committed offsets
		// for this group.
		"auto.offset.reset": "latest",
		// Whether or not we store offsets automatically.
		"enable.auto.offset.store": false,
	})

	if err != nil {
		log.Printf("Failed to create consumer: %s\n", err)
		//childSpan.SetStatus(codes.Error, "consumer failed")
		//childSpan.RecordError(err)
		os.Exit(1)
	}

	log.Printf("Created Consumer %v\n", c)

	err = c.Subscribe(topics, nil)

	run := true

	for run {
		select {
		case sig := <-sigchan:
			log.Printf("Caught signal %v: terminating\n", sig)
			run = false
		default:
			ev := c.Poll(100)
			if ev == nil {
				continue
			}

			switch e := ev.(type) {
			case *kafka.Message:
				carrier := propagation.MapCarrier{}
				for _, v := range e.Headers {
					carrier.Set(v.Key, string(v.Value))
				}
				ctx := otel.GetTextMapPropagator().Extract(context.Background(), carrier)

				// Process the message received.
				tr := otel.Tracer("consumer")
				_, span := tr.Start(ctx, "consume message with headers ", trace.WithAttributes(
					semconv.MessagingOperationProcess,
				))
				defer span.End()
				log.Printf("%% Message on %s:\n%s\n",
					e.TopicPartition, string(e.Value))
				if e.Headers != nil {
					log.Printf("%% Headers: %v\n", e.Headers)
				}
				span.SetAttributes(attribute.String("stringAttr", "Reading Kafka Message : "+string(e.Value)))

				// We can store the offsets of the messages manually or let
				// the library do it automatically based on the setting
				// enable.auto.offset.store. Once an offset is stored, the
				// library takes care of periodically committing it to the broker
				// if enable.auto.commit isn't set to false (the default is true).
				// By storing the offsets manually after completely processing
				// each message, we can ensure atleast once processing.
				_, err := c.StoreMessage(e)
				if err != nil {
					log.Printf("%% Error storing offset after message %s:\n",
						e.TopicPartition)
				}
			case kafka.Error:
				// Errors should generally be considered
				// informational, the client will try to
				// automatically recover.
				// But in this example we choose to terminate
				// the application if all brokers are down.
				log.Fatalf("%% Error: %v: %v\n", e.Code(), e)
				if e.Code() == kafka.ErrAllBrokersDown {
					run = false
				}
			default:
				log.Printf("Ignored %v\n", e)
			}
		}
	}

	log.Printf("Closing consumer\n")
	c.Close()*/
}
