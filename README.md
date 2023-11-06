# Tracing Requests Across Services

Small project showing how to trace requests across services(`spring boot and python/flask`) using Prometheus/Zipkin/Kibana.

More details look at https://www.linkedin.com/pulse/tracing-requests-across-multiple-services-rodrigo-rodrigues

### Installation

First run `mvn clean package spring-boot:build-image` to build the docker image.

Then run `docker-compose up`.

* [Spring Cloud Sleuth Headers](https://cloud.spring.io/spring-cloud-sleuth/2.0.x/multi/multi__propagation.html)
* [Spring Boot B3 Propagation Sample](https://github.com/cassiomolin/log-aggregation-spring-boot-elastic-stack)
* [Spring Boot Logstash Sample](https://github.com/classicPintus/spring-boot-elk)
* [Python Flask Logstash Sample](https://www.techchorus.net/blog/logging-from-flask-application-to-elasticsearch-via-logstash/)
* [Spring Boot 3 Otlp Support](https://github.com/spring-projects/spring-boot/issues/37278)
* [Spring Boot 2 Otlp Log Exporter Sample](https://github.com/ff-sdesai/distributed-tracing-spring/tree/main)
* [Grafana OpenTelemetry Spring Boot Support](https://grafana.com/docs/opentelemetry/instrumentation/java/spring-starter/)
* [Spring Boot 3 Tracing](https://github.com/micrometer-metrics/tracing/wiki/Spring-Cloud-Sleuth-3.1-Migration-Guide)