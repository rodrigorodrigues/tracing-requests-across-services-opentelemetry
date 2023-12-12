package com.example.springboot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.DefaultKafkaHeaderMapper;
import org.springframework.kafka.support.converter.MessagingMessageConverter;

import java.math.BigDecimal;
import java.util.Locale;

@SpringBootApplication
public class SimpleRestApplication {
    private static final Logger log = LoggerFactory.getLogger(SimpleRestApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(SimpleRestApplication.class, args);
    }

    @Bean
    CommandLineRunner runner(UserRepository userRepository) {
        return args -> {
            if (userRepository.count() == 0) {
                userRepository.save(new User("admin", new BigDecimal("1000.10"), Locale.getDefault()));
                userRepository.save(new User("user", new BigDecimal("100.10"), Locale.getDefault()));
            }
        };
    }
    @Bean
    MessagingMessageConverter converter() {
        MessagingMessageConverter converter = new MessagingMessageConverter();
        DefaultKafkaHeaderMapper mapper = new DefaultKafkaHeaderMapper();
        mapper.setMapAllStringsOut(true);
        mapper.setEncodeStrings(true);
        converter.setHeaderMapper(mapper);
        return converter;
    }

    @Bean
    public KafkaAdmin.NewTopics createTopics() {
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name("payment-topic")
                        .build(),
                TopicBuilder.name("update-payment-topic")
                        .build());
    }

    @Bean
    public ProducerFactory<String, Object> producerFactory(KafkaProperties kafkaProperties) {
        log.debug("AopConfiguration:producerFactory:kafkaProperties: {}", kafkaProperties);
        return new DefaultKafkaProducerFactory<>(kafkaProperties.buildProducerProperties());
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        log.debug("AopConfiguration:kafkaTemplate:producerFactory: {}", producerFactory);
        KafkaTemplate<String, Object> kafkaTemplate = new KafkaTemplate<>(producerFactory);
        kafkaTemplate.setObservationEnabled(true);
        kafkaTemplate.setMessageConverter(converter());
        return kafkaTemplate;
    }

}
