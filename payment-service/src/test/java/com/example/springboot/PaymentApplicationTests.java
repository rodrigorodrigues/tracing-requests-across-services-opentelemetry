package com.example.springboot;

import com.example.schema.avro.CheckStatus;
import com.example.schema.avro.UpdatePayment;
import com.example.springboot.model.*;
import com.example.springboot.repository.PaymentRepository;
import com.example.springboot.repository.UserRepository;
import com.example.springboot.service.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaString;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.MultiValueMap;
import org.springframework.util.MultiValueMapAdapter;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import redis.embedded.RedisServer;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.StreamSupport;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser;
import static org.springframework.web.reactive.function.BodyInserters.fromFormData;
import static org.springframework.web.reactive.function.BodyInserters.fromValue;

@SpringBootTest(properties = "logging.level.com.example.springboot=trace")
@AutoConfigureWebTestClient(timeout = "1s")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@AutoConfigureWireMock(port = 0)
@EmbeddedKafka(topics = {"payment-topic", "update-payment-topic"})
@ContextConfiguration(classes = PaymentApplicationTests.TestRedisConfiguration.class)
class PaymentApplicationTests {
    private static final Logger log = LoggerFactory.getLogger(PaymentApplicationTests.class);

    @Autowired
    WebTestClient client;

    @Autowired
    PaymentRepository paymentRepository;

    @Autowired
    PaymentService paymentService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    EmbeddedKafkaBroker embeddedKafka;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    KafkaProperties kafkaProperties;

    Producer<String, UpdatePayment> producer;

    Consumer<String, com.example.schema.avro.Payment> consumer;

    @TestConfiguration
    static class TestRedisConfiguration {

        private RedisServer redisServer;

        public TestRedisConfiguration(RedisProperties redisProperties) {
            this.redisServer = new RedisServer(redisProperties.getPort());
        }

        @PostConstruct
        public void postConstruct() {
            redisServer.start();
        }

        @PreDestroy
        public void preDestroy() {
            redisServer.stop();
        }
    }

    @BeforeEach
    void setup() throws IOException {
        stubFor(WireMock.post(urlPathEqualTo("/api/v1/errors"))
                .willReturn(aResponse()
                        .withStatus(200)));

        UserAuth admin = userRepository.findById("admin")
                .switchIfEmpty(userRepository.save(new UserAuth("admin", new BigDecimal("1000.10"), "10 Street, Dublin, Ireland", passwordEncoder.encode("admin@123"), "Admin Santos")))
                .block();
        assertThat(admin).isNotNull();
        log.info("Created user: {}", admin);

        UserAuth user = userRepository.findById("user")
                .switchIfEmpty(userRepository.save(new UserAuth("user", new BigDecimal("100.10"), "10 Street, Dublin, Ireland", passwordEncoder.encode("user@123"), "User Rodrigues")))
                .block();
        assertThat(user).isNotNull();
        log.info("Created user: {}", user);

        UserAuth dummyUser = userRepository.findById("dummy_user")
                .switchIfEmpty(userRepository.save(new UserAuth("dummy_user", new BigDecimal("100.10"), "10 Street, Dublin, Ireland", passwordEncoder.encode("user@123"), "User Rodrigues")))
                .block();
        assertThat(dummyUser).isNotNull();
        log.info("Created user: {}", dummyUser);

        RequestPaymentDto requestPaymentDto = new RequestPaymentDto(UUID.randomUUID().toString(), new BigDecimal("1000.50"), user.getUsername(), Instant.now());
        RequestPaymentDto requestPaymentDto2 = new RequestPaymentDto(UUID.randomUUID().toString(), new BigDecimal("100.50"), admin.getUsername(), Instant.now());
        paymentRepository.count()
                .filter(c -> c == 0)
                .map(c -> paymentRepository.saveAll(Arrays.asList(new Payment(null, requestPaymentDto, admin.getUsername()), new Payment(null, requestPaymentDto2, user.getUsername()))))
                .flatMap(Flux::count)
                .subscribe(c -> log.debug("Saved Default Payments:size: {}", c));

        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("testT", "false", embeddedKafka);
        consumerProps.putAll(kafkaProperties.getProperties());
        consumerProps.putAll(kafkaProperties.getConsumer().getProperties());
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class);
        DefaultKafkaConsumerFactory<String, com.example.schema.avro.Payment> cf = new DefaultKafkaConsumerFactory<>(consumerProps);
        consumer = cf.createConsumer();
        embeddedKafka.consumeFromAnEmbeddedTopic(consumer, "payment-topic");

        Map<String, Object> producerProps = KafkaTestUtils.producerProps(embeddedKafka);
        producerProps.putAll(kafkaProperties.getProducer().getProperties());
        producerProps.putAll(kafkaProperties.getProperties());
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        log.info("producerProps: {}", producerProps);
        DefaultKafkaProducerFactory<String, UpdatePayment> pf = new DefaultKafkaProducerFactory<>(producerProps);
        producer = pf.createProducer();

        SchemaString schemaString = new SchemaString(com.example.schema.avro.Payment.getClassSchema().toString());
        registerSchema("payment-topic", 1, schemaString);

        schemaString = new SchemaString(com.example.schema.avro.UpdatePayment.getClassSchema().toString());
        registerSchema("update-payment-topic", 2, schemaString);
    }

    private void registerSchema(String topic, Integer schemaId, SchemaString schemaString) throws IOException {
        stubFor(WireMock.get(urlPathMatching("/schemas/ids/"+schemaId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(schemaString.toJson())));

        stubFor(post(urlPathMatching("/subjects/"+topic+"-value/.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(String.format("{\"id\":\"%s\"}", schemaId))));
    }

    @AfterEach
    void tearDown() {
        if (consumer != null) {
            consumer.close();
        }
        if (producer != null) {
            producer.close();
        }
        StepVerifier.create(paymentRepository.deleteAll())
                .expectNextCount(0)
                .verifyComplete();
        StepVerifier.create(userRepository.deleteAll())
                .expectNextCount(0)
                .verifyComplete();
    }

    @Test
    @WithMockUser(username = "admin", authorities = "ADMIN")
    void contextLoads() throws Exception {
        List<ResponsePaymentDto> payments = client.mutate().responseTimeout(Duration.ofSeconds(6)).build()
                .get().uri("/api/v1/dashboard/payments")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(ResponsePaymentDto.class)
                .returnResult()
                .getResponseBody();

        assertThat(payments).isNotEmpty();

        ResponsePaymentDto payment = payments.get(0);
        assertThat(payment).isNotNull();

        client.get().uri("/api/v1/payments/" + payment.requestId())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.requestId").isEqualTo(payment.requestId())
                .jsonPath("$.total").isNumber()
                .jsonPath("$.status").isEqualTo(PaymentStatus.PROCESSING.name())
                .jsonPath("$.usernameFrom").isNotEmpty()
                .jsonPath("$.usernameTo").isNotEmpty();

        client.get().uri("/api/v1/payments/" + UUID.randomUUID())
                .exchange()
                .expectStatus().isNotFound();

        final UUID requestId = UUID.randomUUID();
        String json = new String(client.mutateWith(csrf()).post().uri("/api/v1/payments")
                        .header("requestId", requestId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(fromValue(String.format("{\"requestId\":\"%s\",\"total\":\"100.05\",\"usernameTo\":\"user\"}", requestId)))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.requestId").isNotEmpty()
                .jsonPath("$.status").isEqualTo(PaymentStatus.PROCESSING.name())
                .jsonPath("$.processedAt").doesNotExist()
                .jsonPath("$.message").doesNotExist()
                .returnResult()
                .getResponseBody(), StandardCharsets.UTF_8);

        assertThat(json).isNotEmpty();

        payment = objectMapper.readValue(json, ResponsePaymentDto.class);

        ConsumerRecord<String, com.example.schema.avro.Payment> record = consumeMessage(requestId.toString());
        assertThat(record).isNotNull();
        com.example.schema.avro.Payment avroPayment = record.value();
        assertThat(avroPayment).isNotNull();
        assertThat(avroPayment.getTotal()).isEqualTo(payment.total());
        assertThat(avroPayment.getRequestId()).isEqualTo(payment.requestId());
        assertThat(avroPayment.getCreatedAt()).isNotNull();
        assertThat(avroPayment.getUsernameFrom()).isEqualTo("admin");
        assertThat(avroPayment.getUsernameTo()).isEqualTo(payment.usernameTo());
        assertThat(avroPayment.getUsernameToAddress()).isNotBlank();
        Headers headers = record.headers();
        assertThat(headers).isNotEmpty();
        assertThat(Arrays.stream(headers.toArray()).map(Header::key)).contains("X-B3-TraceId", "X-B3-SpanId");

        UpdatePayment updatePayment = new UpdatePayment(avroPayment.getRequestId(), Instant.now(), "User is invalid", CheckStatus.SANCTION_CHECK, true);
        ProducerRecord<String, UpdatePayment> producerRecord = new ProducerRecord<>("update-payment-topic", null, updatePayment.getRequestId(), updatePayment, headers);
        Object object = producer.send(producerRecord).get();
        assertThat(object).isNotNull();

        await()
                .atMost(5, TimeUnit.SECONDS)
                .pollDelay(100, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> client.mutateWith(csrf()).get().uri("/api/v1/payments/" + requestId)
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody()
                        .jsonPath("$.message").isNotEmpty()
                        .jsonPath("$.processedAt").isNotEmpty()
                        .jsonPath("$.status").isEqualTo(PaymentStatus.DECLINED.name()));

        client.mutateWith(csrf()).post().uri("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(fromValue("{\"requestId\":null,\"total\":\"100.05\",\"usernameTo\":\"user\"}"))
                .exchange()
                .expectStatus().isBadRequest();

        client.mutateWith(csrf()).post().uri("/api/v1/payments/declinePayment/" + payment.requestId())
                .exchange()
                .expectStatus().is2xxSuccessful();

        client.mutateWith(csrf()).get().uri("/api/v1/payments/" + payment.requestId())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo(PaymentStatus.DECLINED.name());
    }

    private ConsumerRecord<String, com.example.schema.avro.Payment> consumeMessage(String requestId) {
        ConsumerRecords<String, com.example.schema.avro.Payment> consumerRecords = consumer.poll(Duration.ofSeconds(5));
        assertThat(consumerRecords.count()).isPositive();
        return StreamSupport.stream(consumerRecords.spliterator(), false)
                .filter(record -> record.value().getRequestId().equals(requestId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Not found requestId in Kafka: "+requestId));
    }

    @Test
    @WithMockUser(username = "user")
    void completePayment() throws Exception {
        client.mutateWith(csrf()).get().uri("/api/v1/account")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.balance").isEqualTo("100.1");

        final UUID requestId = UUID.randomUUID();
        String json = new String(client.mutateWith(csrf()).post().uri("/api/v1/payments")
                .header("requestId", requestId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(fromValue(String.format("{\"requestId\":\"%s\",\"total\":\"100.05\",\"usernameTo\":\"admin\"}", requestId)))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.requestId").isNotEmpty()
                .jsonPath("$.status").isEqualTo(PaymentStatus.PROCESSING.name())
                .jsonPath("$.processedAt").doesNotExist()
                .jsonPath("$.message").doesNotExist()
                .returnResult()
                .getResponseBody(), StandardCharsets.UTF_8);

        assertThat(json).isNotEmpty();

        ResponsePaymentDto responsePaymentDto = objectMapper.readValue(json, ResponsePaymentDto.class);

        ConsumerRecord<String, com.example.schema.avro.Payment> record = consumeMessage(requestId.toString());
        assertThat(record).isNotNull();
        com.example.schema.avro.Payment avroPayment = record.value();
        assertThat(avroPayment).isNotNull();
        assertThat(avroPayment.getTotal()).isEqualTo(responsePaymentDto.total());
        assertThat(avroPayment.getRequestId()).isEqualTo(responsePaymentDto.requestId());
        assertThat(avroPayment.getCreatedAt()).isNotNull();
        assertThat(avroPayment.getUsernameFrom()).isEqualTo("user");
        assertThat(avroPayment.getUsernameTo()).isEqualTo(responsePaymentDto.usernameTo());
        assertThat(avroPayment.getUsernameToAddress()).isNotBlank();
        Headers headers = record.headers();
        assertThat(headers).isNotEmpty();
        assertThat(Arrays.stream(headers.toArray()).map(Header::key)).contains("X-B3-TraceId", "X-B3-SpanId");

        UpdatePayment updatePayment = new UpdatePayment(avroPayment.getRequestId(), Instant.now(), null, CheckStatus.SANCTION_CHECK, false);
        ProducerRecord<String, UpdatePayment> producerRecord = new ProducerRecord<>("update-payment-topic", null, updatePayment.getRequestId(), updatePayment, headers);
        Object object = producer.send(producerRecord).get();
        assertThat(object).isNotNull();
        updatePayment = new UpdatePayment(avroPayment.getRequestId(), Instant.now(), null, CheckStatus.AUTH_CHECK, false);
        producerRecord = new ProducerRecord<>("update-payment-topic", null, updatePayment.getRequestId(), updatePayment, headers);
        object = producer.send(producerRecord).get();
        assertThat(object).isNotNull();
        updatePayment = new UpdatePayment(avroPayment.getRequestId(), Instant.now(), null, CheckStatus.USER_CONFIRMATION_CHECK, false);
        producerRecord = new ProducerRecord<>("update-payment-topic", null, updatePayment.getRequestId(), updatePayment, headers);
        object = producer.send(producerRecord).get();
        assertThat(object).isNotNull();

        await()
                .atMost(10, TimeUnit.SECONDS)
                .pollDelay(100, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> client.mutateWith(csrf()).get().uri("/api/v1/payments/" + requestId)
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody()
                        .jsonPath("$.status").isEqualTo(PaymentStatus.WAITING_FOR_USER_CONFIRMATION.name()));

        List<NotificationResponseDto> notifications = client.mutate().responseTimeout(Duration.ofSeconds(6)).build()
                .mutateWith(mockUser("admin"))
                .get().uri("/api/v1/dashboard/notifications")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(NotificationResponseDto.class)
                .returnResult()
                .getResponseBody();

        assertThat(notifications).isNotEmpty();

        notifications.forEach(n -> client.mutateWith(csrf()).mutateWith(mockUser("admin")).post().uri("/api/v1/dashboard/notifications")
                .header("requestId", n.requestId())
                .contentType(MediaType.APPLICATION_JSON)
                .body(fromValue(new NotificationRequestDto(n.username(), n.requestId(), true)))
                .exchange()
                .expectStatus().isOk());

        await()
                .atMost(10, TimeUnit.SECONDS)
                .pollDelay(100, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> client.mutateWith(csrf()).get().uri("/api/v1/payments/" + requestId)
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody()
                        .jsonPath("$.status").isEqualTo(PaymentStatus.COMPLETED.name())
                        .jsonPath("$.processedAt").isNotEmpty()
                        .jsonPath("$.message").value(containsString("\uD83E\uDD11")));

        client.mutateWith(csrf()).get().uri("/api/v1/account")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.balance").isEqualTo("0.05");

        client.mutateWith(csrf()).mutateWith(mockUser("admin")).get().uri("/api/v1/account")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.balance").isEqualTo("1100.15");

        UUID requestIdNew = UUID.randomUUID();
        client.mutateWith(csrf()).post().uri("/api/v1/payments")
                .header("requestId", requestIdNew.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(fromValue(String.format("{\"requestId\":\"%s\",\"total\":\"100.05\",\"usernameTo\":\"admin\"}", requestIdNew)))
                .exchange()
                .expectStatus().isCreated();

        List<ResponsePaymentDto> payments = client.mutate().responseTimeout(Duration.ofSeconds(6)).build()
                .get().uri("/api/v1/dashboard/payments")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(ResponsePaymentDto.class)
                .returnResult()
                .getResponseBody();

        assertThat(payments).isNotEmpty();
        for (ResponsePaymentDto payment : payments) {
            if (payment.requestId().equals(requestId.toString())) {
                assertThat(payment.status()).isEqualTo(PaymentStatus.PROCESSING);
                assertThat(payment.requestId()).isEqualTo(requestId.toString());
            }
        }
    }

    @Test
    @WithMockUser(username = "user")
    void shouldDeclinePaymentWhenUserNotConfirmPayment() throws Exception {
        final UUID requestId = UUID.randomUUID();
        String json = new String(client.mutateWith(csrf()).post().uri("/api/v1/payments")
                .header("requestId", requestId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(fromValue(String.format("{\"requestId\":\"%s\",\"total\":\"100.05\",\"usernameTo\":\"admin\"}", requestId)))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.requestId").isNotEmpty()
                .jsonPath("$.status").isEqualTo(PaymentStatus.PROCESSING.name())
                .jsonPath("$.processedAt").doesNotExist()
                .jsonPath("$.message").doesNotExist()
                .returnResult()
                .getResponseBody(), StandardCharsets.UTF_8);

        assertThat(json).isNotEmpty();

        ResponsePaymentDto responsePaymentDto = objectMapper.readValue(json, ResponsePaymentDto.class);

        ConsumerRecord<String, com.example.schema.avro.Payment> record = consumeMessage(requestId.toString());
        assertThat(record).isNotNull();
        com.example.schema.avro.Payment avroPayment = record.value();
        assertThat(avroPayment).isNotNull();
        assertThat(avroPayment.getTotal()).isEqualTo(responsePaymentDto.total());
        assertThat(avroPayment.getRequestId()).isEqualTo(responsePaymentDto.requestId());
        assertThat(avroPayment.getCreatedAt()).isNotNull();
        assertThat(avroPayment.getUsernameFrom()).isEqualTo("user");
        assertThat(avroPayment.getUsernameTo()).isEqualTo(responsePaymentDto.usernameTo());
        assertThat(avroPayment.getUsernameToAddress()).isNotBlank();
        Headers headers = record.headers();
        assertThat(headers).isNotEmpty();
        assertThat(Arrays.stream(headers.toArray()).map(Header::key)).contains("X-B3-TraceId", "X-B3-SpanId");

        UpdatePayment updatePayment = new UpdatePayment(avroPayment.getRequestId(), Instant.now(), null, CheckStatus.SANCTION_CHECK, false);
        ProducerRecord<String, UpdatePayment> producerRecord = new ProducerRecord<>("update-payment-topic", null, updatePayment.getRequestId(), updatePayment, headers);
        Object object = producer.send(producerRecord).get();
        assertThat(object).isNotNull();
        updatePayment = new UpdatePayment(avroPayment.getRequestId(), Instant.now(), null, CheckStatus.AUTH_CHECK, false);
        producerRecord = new ProducerRecord<>("update-payment-topic", null, updatePayment.getRequestId(), updatePayment, headers);
        object = producer.send(producerRecord).get();
        assertThat(object).isNotNull();
        updatePayment = new UpdatePayment(avroPayment.getRequestId(), Instant.now(), null, CheckStatus.USER_CONFIRMATION_CHECK, false);
        producerRecord = new ProducerRecord<>("update-payment-topic", null, updatePayment.getRequestId(), updatePayment, headers);
        object = producer.send(producerRecord).get();
        assertThat(object).isNotNull();

        await()
                .atMost(10, TimeUnit.SECONDS)
                .pollDelay(100, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> client.mutateWith(csrf()).get().uri("/api/v1/payments/" + requestId)
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody()
                        .jsonPath("$.status").isEqualTo(PaymentStatus.WAITING_FOR_USER_CONFIRMATION.name()));

        List<NotificationResponseDto> notifications = client.mutate().responseTimeout(Duration.ofSeconds(6)).build()
                .mutateWith(mockUser("admin"))
                .get().uri("/api/v1/dashboard/notifications")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(NotificationResponseDto.class)
                .returnResult()
                .getResponseBody();

        assertThat(notifications).isNotEmpty();

        NotificationResponseDto notificationResponseDto = notifications.stream().filter(p -> p.requestId().equals(requestId.toString()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Not found requestId: " + requestId));

        client.mutateWith(csrf()).mutateWith(mockUser("admin")).post().uri("/api/v1/dashboard/notifications")
                .header("requestId", notificationResponseDto.requestId())
                .contentType(MediaType.APPLICATION_JSON)
                .body(fromValue(new NotificationRequestDto(notificationResponseDto.username(), notificationResponseDto.requestId(), false)))
                .exchange()
                .expectStatus().isOk();

        await()
                .atMost(10, TimeUnit.SECONDS)
                .pollDelay(100, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> client.mutateWith(csrf()).get().uri("/api/v1/payments/" + requestId)
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody()
                        .jsonPath("$.status").isEqualTo(PaymentStatus.DECLINED.name())
                        .jsonPath("$.processedAt").isNotEmpty()
                        .jsonPath("$.message").value(containsString("\uD83D\uDE21 User has declined payment!")));
    }

    @Test
    @WithMockUser(username = "dummy_user")
    void reprocessPayment() throws Exception {
        final String requestId = UUID.randomUUID().toString();
        String json = new String(client.mutateWith(csrf()).post().uri("/api/v1/payments")
                .header("requestId", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(fromValue(String.format("{\"requestId\":\"%s\",\"total\":\"100.05\",\"usernameTo\":\"admin\"}", requestId)))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.requestId").isNotEmpty()
                .jsonPath("$.status").isEqualTo(PaymentStatus.PROCESSING.name())
                .jsonPath("$.processedAt").doesNotExist()
                .jsonPath("$.message").doesNotExist()
                .returnResult()
                .getResponseBody(), StandardCharsets.UTF_8);

        assertThat(json).isNotEmpty();

        ResponsePaymentDto responsePaymentDto = objectMapper.readValue(json, ResponsePaymentDto.class);

        assertThat(responsePaymentDto).isNotNull();

        ConsumerRecord<String, com.example.schema.avro.Payment> record = consumeMessage(requestId);
        assertThat(record).isNotNull();
        com.example.schema.avro.Payment avroPayment = record.value();
        assertThat(avroPayment).isNotNull();
        Headers headers = record.headers();
        assertThat(headers).isNotEmpty();

        UpdatePayment updatePayment = new UpdatePayment(responsePaymentDto.requestId(), Instant.now(), "User is invalid", CheckStatus.SANCTION_CHECK, true);
        ProducerRecord<String, UpdatePayment> producerRecord = new ProducerRecord<>("update-payment-topic", null, updatePayment.getRequestId(), updatePayment, headers);
        Object object = producer.send(producerRecord).get();
        assertThat(object).isNotNull();

        await()
                .atMost(5, TimeUnit.SECONDS)
                .pollDelay(100, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> client.mutateWith(csrf()).get().uri("/api/v1/payments/" + requestId)
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody()
                        .jsonPath("$.message").isNotEmpty()
                        .jsonPath("$.processedAt").isNotEmpty()
                        .jsonPath("$.status").isEqualTo(PaymentStatus.DECLINED.name()));

        client.mutateWith(csrf()).post().uri("/api/v1/payments")
                .header("requestId", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(fromValue(String.format("{\"requestId\":\"%s\",\"total\":\"100.05\",\"usernameTo\":\"admin\"}", requestId)))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.requestId").isNotEmpty()
                .jsonPath("$.status").isEqualTo(PaymentStatus.REPROCESSING.name())
                .jsonPath("$.processedAt").doesNotExist()
                .jsonPath("$.message").doesNotExist();

        updatePayment = new UpdatePayment(requestId, Instant.now(), null, CheckStatus.SANCTION_CHECK, false);
        producerRecord = new ProducerRecord<>("update-payment-topic", null, updatePayment.getRequestId(), updatePayment, headers);
        object = producer.send(producerRecord).get();
        assertThat(object).isNotNull();
        updatePayment = new UpdatePayment(requestId, Instant.now(), null, CheckStatus.AUTH_CHECK, false);
        producerRecord = new ProducerRecord<>("update-payment-topic", null, updatePayment.getRequestId(), updatePayment, headers);
        object = producer.send(producerRecord).get();
        assertThat(object).isNotNull();
        updatePayment = new UpdatePayment(requestId, Instant.now(), null, CheckStatus.USER_CONFIRMATION_CHECK, false);
        producerRecord = new ProducerRecord<>("update-payment-topic", null, updatePayment.getRequestId(), updatePayment, headers);
        object = producer.send(producerRecord).get();
        assertThat(object).isNotNull();

        await()
                .atMost(10, TimeUnit.SECONDS)
                .pollDelay(100, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> client.mutateWith(csrf()).get().uri("/api/v1/payments/" + requestId)
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody()
                        .jsonPath("$.status").isEqualTo(PaymentStatus.WAITING_FOR_USER_CONFIRMATION.name()));

        List<NotificationResponseDto> notifications = client.mutate().responseTimeout(Duration.ofSeconds(6)).build()
                .mutateWith(mockUser("admin"))
                .get().uri("/api/v1/dashboard/notifications")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(NotificationResponseDto.class)
                .returnResult()
                .getResponseBody();

        assertThat(notifications).isNotEmpty();

        notifications.forEach(n -> client.mutateWith(csrf()).mutateWith(mockUser("admin")).post().uri("/api/v1/dashboard/notifications")
                .header("requestId", n.requestId())
                .contentType(MediaType.APPLICATION_JSON)
                .body(fromValue(new NotificationRequestDto(n.username(), n.requestId(), true)))
                .exchange()
                .expectStatus().isOk());

        await()
                .atMost(10, TimeUnit.SECONDS)
                .pollDelay(100, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> client.mutate().responseTimeout(Duration.ofSeconds(3)).build()
                        .mutateWith(csrf()).get().uri("/api/v1/payments/" + requestId)
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody()
                        .jsonPath("$.status").isEqualTo(PaymentStatus.COMPLETED.name())
                        .jsonPath("$.processedAt").isNotEmpty()
                        .jsonPath("$.message").value(containsString("\uD83E\uDD11")));

        client.mutateWith(csrf()).post().uri("/api/v1/payments")
                .header("requestId", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(fromValue(String.format("{\"requestId\":\"%s\",\"total\":\"100.05\",\"usernameTo\":\"admin\"}", requestId)))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.message").value(containsString("Cannot reprocess a COMPLETED payment!"));
    }

    @Test
    @WithMockUser(username = "user")
    void shouldFilterByUser() {
        client.mutate().responseTimeout(Duration.ofSeconds(6)).build()
                .get().uri("/api/v1/dashboard/payments")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(ResponsePaymentDto.class)
                .consumeWith(resp -> assertThat(resp.getResponseBody().get(0).usernameFrom()).isEqualTo("user"));
    }

    @Test
    @WithMockUser(username = "dummy")
    void shouldReturnEmptyList() {
        client.mutate().responseTimeout(Duration.ofSeconds(6)).build()
                .get().uri("/api/v1/dashboard/payments")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$").doesNotExist();
    }

    @Test
    @WithMockUser(username = "dummy")
    void shouldDeclinePayment() {
        Payment payment = paymentRepository.findAll().blockLast();
        assertThat(payment).isNotNull();

        client.mutateWith(csrf())
                .post()
                .uri("/api/v1/payments/declinePayment/" + payment.getRequestId())
                .exchange()
                .expectStatus().is4xxClientError();

        client.mutateWith(csrf())
                .mutateWith(mockUser("test").authorities(new SimpleGrantedAuthority("ADMIN")))
                .post()
                .uri("/api/v1/payments/declinePayment/" + payment.getRequestId())
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @WithMockUser(username = "user")
    void shouldDeclineExpiredPayment() {
        RequestPaymentDto requestPaymentDto = new RequestPaymentDto(UUID.randomUUID().toString(), new BigDecimal("1000.50"), "admin", Instant.now().minusSeconds(121));
        Payment payment = new Payment(null, requestPaymentDto, "user");
        payment.setCreatedAt(requestPaymentDto.createdAt());
        paymentRepository.save(payment)
                .subscribe(c -> log.debug("Created Payments: {}", c));

        await()
                .atMost(10, TimeUnit.SECONDS)
                .pollDelay(1000, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> client.mutateWith(csrf()).get().uri("/api/v1/payments/" + requestPaymentDto.requestId())
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody()
                        .jsonPath("$.message").value(containsString("Payment expired!"))
                        .jsonPath("$.processedAt").isNotEmpty()
                        .jsonPath("$.status").isEqualTo(PaymentStatus.DECLINED.name()));
    }

    @Test
    void shouldRegisterUser() {
        UUID requestId = UUID.randomUUID();
        UserRequestDto userRequestDto = new UserRequestDto("testNewUser", "Test New User", "Test@123", "Test@123", "500 Street, Dublin, Ireland");

        client.mutateWith(csrf()).post().uri("/api/v1/users/register")
                .header("requestId", requestId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(fromValue(userRequestDto))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.permissions").isArray()
                .jsonPath("$.username").isEqualTo("testNewUser")
                .jsonPath("$.balance").isNotEmpty()
                .jsonPath("$.fullName").isEqualTo("Test New User")
                .jsonPath("$.password").doesNotExist();

        client.mutateWith(csrf()).get().uri("/api/v1/account")
                .exchange()
                .expectStatus().is4xxClientError();

        Map<String, List<String>> formData = new HashMap<>();
        formData.put("username", Collections.singletonList("testNewUser"));
        formData.put("password", Collections.singletonList("Test@123"));

        MultiValueMap<String, ResponseCookie> cookies = client.mutateWith(csrf())
                .post().uri("/login")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(fromFormData(new MultiValueMapAdapter<>(formData)))
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader()
                .location("/")
                .returnResult(ResponsePaymentDto.class)
                .getResponseCookies();

        assertThat(cookies).isNotEmpty();

        client.mutateWith(csrf()).get().uri("/api/v1/account")
                .cookie("SESSION", cookies.getFirst("SESSION").getValue())
                .exchange()
                .expectStatus().isOk();

        userRequestDto = new UserRequestDto("testNewUser", "Test New User", "Test@123", "Test@123", "500 Street, Dublin, Ireland");

        client.mutateWith(csrf()).post().uri("/api/v1/users/register")
                .header("requestId", requestId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(fromValue(userRequestDto))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.message").value(containsString("Username already exists:"));

        userRequestDto = new UserRequestDto("", "Test New User", "", "Test@123", "500 Street, Dublin, Ireland");

        client.mutateWith(csrf()).post().uri("/api/v1/users/register")
                .header("requestId", requestId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(fromValue(userRequestDto))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error").hasJsonPath();
    }

}
