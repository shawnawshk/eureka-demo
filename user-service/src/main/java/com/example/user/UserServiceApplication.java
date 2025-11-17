package com.example.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Random;

@SpringBootApplication
@RestController
public class UserServiceApplication {

    private static final Map<String, User> USERS = Map.of(
        "john", new User("john", "John Doe", "john@example.com"),
        "jane", new User("jane", "Jane Smith", "jane@example.com"),
        "bob", new User("bob", "Bob Johnson", "bob@example.com")
    );

    @GetMapping("/users/{username}")
    public User getUser(@PathVariable String username) {
        return USERS.getOrDefault(username, generateRandomUser());
    }

    private User generateRandomUser() {
        String[] names = {"Alice", "Charlie", "David", "Emma", "Frank"};
        String randomName = names[new Random().nextInt(names.length)];
        return new User("random", randomName, randomName.toLowerCase() + "@example.com");
    }

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}

record User(String username, String name, String email) {}
