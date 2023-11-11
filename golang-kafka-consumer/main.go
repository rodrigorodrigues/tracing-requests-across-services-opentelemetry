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
	otlp_url := GetEnv("OTEL_EXPORTER_OTLP_ENDPOINT")
	tracerProvider := util.InitTracer(service, otlp_url, GetEnvAsBool("INSECURE_MODE"))
	defer tracerProvider(context.Background())

	meterProvider, err := util.MeterProvider(service, otlp_url)
	if err != nil {
		panic(err)
	}
	defer meterProvider.Shutdown(context.Background())

	bootstrapServers := GetEnv("KAFKA_BOOTSTRAP_SERVERS")
	group := GetEnv("KAFKA_GROUP")
	topics := GetEnv("KAFKA_TOPIC")

	util.ReadKafkaMessages(bootstrapServers, topics, group, GetEnv("NODEJS_EXPRESS_SERVER_URL"), meterProvider)
}
