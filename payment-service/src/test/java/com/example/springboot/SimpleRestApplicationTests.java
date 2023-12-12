package com.example.springboot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaString;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"ERROR_SERVICE_URL=http://localhost:${wiremock.server.port}", "TOPIC_NAME=topic_test"})
@AutoConfigureMockMvc
@AutoConfigureWireMock(port = 0)
@EmbeddedKafka(topics = "topic_test")
class SimpleRestApplicationTests {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    PaymentRepository paymentRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    EmbeddedKafkaBroker embeddedKafka;

    @Autowired
    KafkaProperties kafkaProperties;

    Consumer<String, com.example.schema.avro.Payment> consumer;

    @BeforeEach
    void setup() throws IOException {
        stubFor(WireMock.post(urlPathEqualTo("/v1/errors"))
                .willReturn(aResponse()
                        .withStatus(200)));

        User admin = new User("admin", new BigDecimal("1000.50"), Locale.getDefault());
        User user = new User("user", new BigDecimal("100.50"), Locale.getDefault());
        userRepository.saveAll(Arrays.asList(admin, user));

        PaymentDto paymentDto = new PaymentDto(UUID.randomUUID().toString(), new BigDecimal("1000.50"), null, "admin", "user", Locale.getDefault(), Locale.getDefault(), Instant.now(), null);
        paymentRepository.save(new Payment(paymentDto, admin, user));

        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("testT", "false", embeddedKafka);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class);
        consumerProps.putAll(kafkaProperties.getProperties());
        consumerProps.putAll(kafkaProperties.getConsumer().getProperties());
        DefaultKafkaConsumerFactory<String, com.example.schema.avro.Payment> cf = new DefaultKafkaConsumerFactory<>(consumerProps);
        consumer = cf.createConsumer();
        embeddedKafka.consumeFromAllEmbeddedTopics(consumer);

        SchemaString schemaString = new SchemaString(com.example.schema.avro.Payment.getClassSchema().toString());
        stubFor(WireMock.get(urlPathMatching("/schemas/ids/.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(schemaString.toJson())));

        stubFor(WireMock.post(urlPathMatching("/subjects/.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"1\"}")));
    }

    @AfterEach
    void tearDown() {
        paymentRepository.deleteAll();
        userRepository.deleteAll();
        consumer.close();
    }

    @Test
    void contextLoads() throws Exception {
        String json = mockMvc.perform(get("/v1/payments?usernameFrom=admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$..requestId", hasSize(1)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(json).isNotEmpty();

        PaymentDto[] payments = objectMapper.readValue(json, PaymentDto[].class);

        assertThat(payments).hasSize(1);
        PaymentDto payment = payments[0];

        mockMvc.perform(get("/v1/payments/"+payment.requestId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value(payment.requestId()))
                .andExpect(jsonPath("$.total").isNumber())
                .andExpect(jsonPath("$.status").value(PaymentStatus.PROCESSING.name()))
                .andExpect(jsonPath("$.usernameFrom").value("admin"))
                .andExpect(jsonPath("$.usernameTo").value("user"));

        mockMvc.perform(get("/v1/payments/"+UUID.randomUUID()))
                .andExpect(status().isNotFound());

        UUID requestId = UUID.randomUUID();
        json = mockMvc.perform(post("/v1/payments")
                        .header("requestId", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"requestId\":\"%s\",\"total\":\"100.05\",\"usernameFrom\":\"user\",\"usernameTo\":\"admin\"}", requestId)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.status").value(PaymentStatus.PROCESSING.name()))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(json).isNotEmpty();

        payment = objectMapper.readValue(json, PaymentDto.class);

        ConsumerRecords<String, com.example.schema.avro.Payment> consumerRecords = consumer.poll(Duration.ofSeconds(1));
        assertThat(consumerRecords.count()).isPositive();
        ConsumerRecord<String, com.example.schema.avro.Payment> record = consumerRecords.iterator().next();
        assertThat(record).isNotNull();
        com.example.schema.avro.Payment avroPayment = record.value();
        assertThat(avroPayment).isNotNull();
        assertThat(avroPayment.getTotal()).isEqualTo(payment.total());
        assertThat(avroPayment.getRequestId()).isEqualTo(payment.requestId());
        assertThat(avroPayment.getCreatedAt()).isNotNull();
        assertThat(avroPayment.getUsernameFrom()).isEqualTo(payment.usernameFrom());
        assertThat(avroPayment.getUsernameTo()).isEqualTo(payment.usernameTo());
        assertThat(avroPayment.getUsernameToCountry()).isEqualTo(Locale.getDefault().getCountry());
        Headers headers = record.headers();
        assertThat(headers).isNotEmpty();
        assertThat(Arrays.stream(headers.toArray()).map(Header::key)).contains("X-B3-TraceId", "X-B3-SpanId", "requestId");

        mockMvc.perform(post("/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":null,\"total\":\"100.05\",\"usernameFrom\":\"user\",\"usernameTo\":\"admin\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/v1/payments/declinePayment/"+payment.requestId()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/v1/payments/"+payment.requestId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(PaymentStatus.DECLINED.name()));
    }

}
