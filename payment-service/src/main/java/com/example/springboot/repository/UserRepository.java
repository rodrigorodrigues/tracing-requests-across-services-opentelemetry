package com.example.springboot.repository;

import com.example.springboot.model.UserAuth;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface UserRepository extends ReactiveCrudRepository<UserAuth, Long> {
    @Query("SELECT * FROM user_auth WHERE username = :username")
    Mono<UserAuth> findById(@Param("username") String username);
}
