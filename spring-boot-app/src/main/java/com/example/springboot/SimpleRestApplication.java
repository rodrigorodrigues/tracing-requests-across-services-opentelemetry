package com.example.springboot;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mapstruct.Mapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

@SpringBootApplication
@Slf4j
public class SimpleRestApplication {

    public static void main(String[] args) {
        SpringApplication.run(SimpleRestApplication.class, args);
    }

    @Bean
    RestTemplate restTemplate(RestTemplateBuilder restTemplateBuilder, @Value("${address-service-url:http://localhost:8090}") String addressServiceUrl) {
        return restTemplateBuilder.rootUri(addressServiceUrl).build();
    }

    @Bean
    PersonRepository personRepository() {
        return new PersonRepository() {};
    }

    @RestController
    @RequestMapping("/v1/persons")
    @AllArgsConstructor
    class PersonController {
        private final PersonService personService;

        @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
        public ResponseEntity<List<PersonDto>> get(@RequestParam(name = "name", required = false) String name) {
            log.info("Return list of persons");
            if (StringUtils.hasText(name)) {
                return ResponseEntity.ok(personService.getByName(name));
            } else {
                return ResponseEntity.ok(personService.getAll());
            }
        }

        @GetMapping("/{id}")
        public ResponseEntity<PersonDto> findById(@PathVariable Integer id) {
            log.info("Return person by id: "+id);
            return personService.findById(id)
                    .map(ResponseEntity::ok)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found id: " + id));
        }

        @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
        public ResponseEntity<PersonDto> save(@RequestBody @Valid PersonDto person) {
            return ResponseEntity.status(HttpStatus.CREATED).body(personService.save(person));
        }

        @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE )
        public ResponseEntity<PersonDto> update(@PathVariable Integer id, @RequestBody @Valid PersonDto person) {
            return ResponseEntity.ok(personService.update(id, person));
        }

        @DeleteMapping("/{id}")
        public ResponseEntity<?> delete(@PathVariable Integer id) {
            personService.removeById(id);
            return ResponseEntity.noContent().build();
        }
    }

    @Mapper(componentModel = "spring")
    interface PersonMapper {
        Person convertToModel(PersonDto personDto);

        PersonDto convertToDto(Person person);

        List<PersonDto> convertToDto(List<Person> persons);
    }

    @AllArgsConstructor
    @Service
    class PersonService {
        private final PersonRepository personRepository;
        private final PersonMapper personMapper;
        private final RestTemplate restTemplate;

        private PersonDto.Address getAddressByPersonId(Integer id) {
            try {
                return restTemplate.getForObject("/v1/addresses/person/{id}", PersonDto.Address.class, id);
            } catch (RestClientException rce) {
                log.warn("Could not retrieve address", rce);
                return null;
            }
        }

        public List<PersonDto> getAll() {
            return personMapper.convertToDto(personRepository.getAll());
        }

        public List<PersonDto> getByName(String name) {
            return getAll().stream()
                    .filter(person -> person.getName().startsWith(name))
                    .collect(Collectors.toList());
        }

        public Optional<PersonDto> findById(Integer id) {
            return Optional.ofNullable(personMapper.convertToDto(personRepository.findById(id)))
                    .map(p -> {
                        p.setAddress(getAddressByPersonId(p.id));
                        return p;
                    });
        }

        public PersonDto save(PersonDto person) {
            if (person.getId() == null) {
                person.setId(getAll().size() + 1);
            }
            return personMapper.convertToDto(personRepository.save(personMapper.convertToModel(person)));
        }

        public PersonDto update(Integer id, PersonDto person) {
            if (findById(id) == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found id: "+id);
            }
            person.setId(id);
            return personMapper.convertToDto(personRepository.update(person.getId(), personMapper.convertToModel(person)));
        }

        public void removeById(Integer id) {
            personRepository.removeById(id);
        }
    }

    @Repository
    interface PersonRepository {
        Map<Integer, Person> data = new HashMap<>(Map.ofEntries(
                Map.entry(1, new Person(1, "Test")),
                Map.entry(2, new Person(2, "Anonymous"))
        ));

        default List<Person> getAll() {
            return new ArrayList<>(data.values());
        }

        default Person findById(Integer id) {
            return data.get(id);
        }

        default Person save(Person person) {
            return data.computeIfAbsent(person.getId(), i -> person);
        }

        default Person update(Integer id, Person person) {
            data.put(id, person);
            return data.get(id);
        }

        default void removeById(Integer id) {
            data.remove(id);
        }
    }

    @Data
    @NoArgsConstructor
    static class PersonDto {
        private Integer id;
        @NotBlank
        private String name;
        private Address address;

        @Data
        @NoArgsConstructor
        static class Address {
            private Integer id;
            private String street;
        }
    }

    @Data
    @AllArgsConstructor
    static class Person {
        private Integer id;
        private String name;
    }

}
