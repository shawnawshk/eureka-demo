package com.example.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Random;

@SpringBootApplication
public class OrderServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}

@Configuration
class RestTemplateConfig {
    @Bean
    @LoadBalanced
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}

@RestController
class OrderController {
    private final RestTemplate restTemplate;

    public OrderController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @GetMapping("/orders/{username}")
    public Order getOrder(@PathVariable String username) {
        User user = restTemplate.getForObject("http://user-service/users/" + username, User.class);
        return new Order(
            "ORD-" + new Random().nextInt(10000),
            user.username(),
            user.name(),
            "Product-" + new Random().nextInt(100),
            99.99,
            LocalDateTime.now().toString()
        );
    }
}

record User(String username, String name, String email) {}
record Order(String orderId, String username, String customerName, String product, double amount, String orderDate) {}
