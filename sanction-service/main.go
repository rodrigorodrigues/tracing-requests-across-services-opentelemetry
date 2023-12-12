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
	"github.com/sirupsen/logrus"
	"io"
	"os"
	"strconv"
	"strings"
	"time"
)

var (
	loadEnvFlag = true
	log         *logrus.Logger
)

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
	log.Info(fmt.Sprintf("Env = %+v\tvalue = %v\n", key, value))
	return value
}

func GetEnvAsBool(key string) bool {
	value, err := strconv.ParseBool(GetEnv(key))
	if err != nil {
		return false
	}
	return value
}

func loadEnv() {
	env := ".env"
	environment := os.Getenv("ENVIRONMENT")
	if environment != "" {
		env += "." + environment
	}

	log.Info(fmt.Sprintf("Env = %+v\n", env))
	if err := godotenv.Load(env); err != nil {
		panic(err)
	}
}

type MyFormatter struct {
}

func (f *MyFormatter) Format(entry *logrus.Entry) ([]byte, error) {
	msg := fmt.Sprintf("%v %v serviceName='sanction-service-go' %v\n", entry.Time.Format(time.RFC3339), strings.ToUpper(entry.Level.String()), entry.Message)
	return []byte(msg), nil
}

func main() {
	log = logrus.New()
	/*formatter := &logrus.TextFormatter{
		ForceColors:      false, //Enable this for coloured output
		DisableColors:    false,
		TimestampFormat:  "2006-01-02T15:04:05.000",
		DisableTimestamp: false,
		FullTimestamp:    true,
	}*/
	log.SetFormatter(new(MyFormatter))
	//log.SetFormatter(formatter)
	if GetEnvAsBool("SET_LOG_FILE") {
		// Open a file if it exist or create one if file does not exist
		file, err := os.OpenFile(GetEnv("LOG_FILE"), os.O_WRONLY|os.O_APPEND|os.O_CREATE, 0644)
		if err != nil {
			panic(err)
		}
		defer file.Close()
		log.Out = io.MultiWriter(file, os.Stdout)
	}

	ctx := context.Background()

	serviceName := GetEnv("SERVICE_NAME")
	otlpUrl := GetEnv("OTEL_EXPORTER_OTLP_ENDPOINT") //"http://localhost:9411/api/v2/spans"

	bootstrapServers := GetEnv("KAFKA_BOOTSTRAP_SERVERS")
	group := GetEnv("KAFKA_GROUP")
	topics := GetEnv("KAFKA_TOPIC")

	log.Info("Starting to reading Kafka message")

	ProcessMessages(bootstrapServers, topics, group, GetEnv("NODEJS_EXPRESS_SERVER_URL"), "http://localhost:8081", ctx, serviceName, otlpUrl, log)
}
