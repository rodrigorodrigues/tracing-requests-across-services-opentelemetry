package com.example.springboot;

import com.example.springboot.config.PaymentProperties;
import com.example.springboot.model.NotificationResponseDto;
import com.example.springboot.model.UserAuth;
import com.example.springboot.repository.UserRepository;
import io.r2dbc.spi.ConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.DefaultKafkaHeaderMapper;
import org.springframework.kafka.support.converter.MessagingMessageConverter;
import org.springframework.r2dbc.connection.init.ConnectionFactoryInitializer;
import org.springframework.r2dbc.connection.init.ResourceDatabasePopulator;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Arrays;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;

@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties(PaymentProperties.class)
public class PaymentApplication {
    private static final Logger log = LoggerFactory.getLogger(PaymentApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(PaymentApplication.class, args);
    }

    @Bean
    ReactiveRedisOperations<String, NotificationResponseDto> redisOperations(ReactiveRedisConnectionFactory factory) {
        Jackson2JsonRedisSerializer<NotificationResponseDto> serializer = new Jackson2JsonRedisSerializer<>(NotificationResponseDto.class);

        RedisSerializationContext.RedisSerializationContextBuilder<String, NotificationResponseDto> builder =
                RedisSerializationContext.newSerializationContext(new StringRedisSerializer());

        RedisSerializationContext<String, NotificationResponseDto> context = builder.value(serializer).build();

        return new ReactiveRedisTemplate<>(factory, context);
    }

    @Bean
    ConnectionFactoryInitializer initializer(ConnectionFactory connectionFactory) {
        ConnectionFactoryInitializer initializer = new ConnectionFactoryInitializer();
        initializer.setConnectionFactory(connectionFactory);
        initializer.setDatabasePopulator(new ResourceDatabasePopulator(new ClassPathResource("schema.sql")));

        return initializer;
    }

    @Bean
    RouterFunction<ServerResponse> index() {
        return RouterFunctions.route(GET("/"), req ->
                ServerResponse.temporaryRedirect(URI.create("/api/v1/account")).build());
    }

    @Bean
    CommandLineRunner runner(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> userRepository.count()
                .filter(c -> c == 0)
                .map(c -> userRepository.saveAll(Arrays.asList(new UserAuth("admin", new BigDecimal("1000.10"), "10 Street, Dublin, Ireland", passwordEncoder.encode("admin@123"), "Admin Santos"),
                                new UserAuth("user", new BigDecimal("100.10"), "10 Street, Dublin, Ireland", passwordEncoder.encode("user@123"), "User Rodrigues"),
                                new UserAuth("anonymous", new BigDecimal("10000.10"), "10 Street, Kabul, Afghanistan", passwordEncoder.encode("test@123"), "Anonymous invalid")
                                )))
                .flatMap(Flux::count)
                .subscribe(c -> log.debug("Saved Default Users:size: {}", c));
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
        return new DefaultKafkaProducerFactory<>(kafkaProperties.buildProducerProperties());
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        KafkaTemplate<String, Object> kafkaTemplate = new KafkaTemplate<>(producerFactory);
        kafkaTemplate.setObservationEnabled(true);
        kafkaTemplate.setMessageConverter(converter());
        return kafkaTemplate;
    }
}
