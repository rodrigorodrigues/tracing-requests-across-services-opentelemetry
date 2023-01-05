# Tracing Requests Across Services

Small project showing how to trace requests across services(`spring boot and python/flask`) using Prometheus/Zipkin/Kibana.

More details look at https://www.linkedin.com/pulse/tracing-requests-across-multiple-services-rodrigo-rodrigues

### Installation

First run `mvn clean package spring-boot:build-image` to build the docker image.

Then run `docker-compose up`.

* [Spring Cloud Sleuth Headers](https://cloud.spring.io/spring-cloud-sleuth/2.0.x/multi/multi__propagation.html)
* [Spring Boot B3 Propagation Sample](https://github.com/cassiomolin/log-aggregation-spring-boot-elastic-stack)
* [Spring Boot Logstash Sample](https://github.com/classicPintus/spring-boot-elk)
