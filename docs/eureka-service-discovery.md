# Eureka Service Discovery - How It Works

## How Eureka Client Caching Works

Eureka clients use **local caching** to avoid making a request to Eureka Server for every service call.

### Eureka Client Behavior:

1. **Initial Registration**: When a service starts, it registers with Eureka Server
2. **Registry Fetch**: The client fetches the full service registry from Eureka Server
3. **Local Cache**: The registry is cached locally in memory
4. **Periodic Refresh**: The cache is refreshed every 30 seconds (configurable via `eureka.client.registry-fetch-interval-seconds`)
5. **Service Calls**: When making a call, the client looks up the target service address from its **local cache** - no call to Eureka Server

### For Each Cross-Service Call:
- **Only 1 request**: Direct call to the target service using the cached address
- **No Eureka Server call** during the actual service invocation

### Background Operations (asynchronous):
- **Heartbeat**: Every 30 seconds, services send heartbeats to Eureka to stay registered
- **Registry Refresh**: Every 30 seconds, clients refresh their local cache from Eureka
- **Service Discovery**: Happens in the background, not during your API calls

### Load Balancing:
When using Spring Cloud LoadBalancer, if multiple instances of a service are registered, the client-side load balancer picks one from the local cache using a load balancing algorithm (default: round-robin).

This design makes Eureka highly efficient - the Eureka Server could even go down temporarily, and services would continue communicating using their cached registry information.

---

## Handling Service Failures

### The Problem Window:

When a service instance goes down, there's a **delay window** before all clients know about it:

1. **Service goes down** at time T
2. **Eureka Server detects it** after ~90 seconds (3 missed heartbeats of 30s each)
3. **Clients refresh their cache** up to 30 seconds later
4. **Total delay**: Up to **2 minutes** where clients might call a dead instance

### What Happens During This Window:

When a client tries to call a non-available service:

1. **Connection Timeout/Failure**: The HTTP client gets a connection error
2. **Retry Logic**: Spring Cloud LoadBalancer can retry with another instance (if available)
3. **Circuit Breaker**: If using Resilience4j/Hystrix, the circuit opens after repeated failures
4. **Error Propagation**: Eventually returns an error to the caller

---

## Solutions to Handle Service Failures

### 1. Retry with Another Instance (Built-in)

```yaml
spring:
  cloud:
    loadbalancer:
      retry:
        enabled: true
```

If one instance fails, it automatically tries another registered instance.

### 2. Circuit Breaker Pattern (Resilience4j)

Add dependency:
```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-circuitbreaker-resilience4j</artifactId>
</dependency>
```

Use in code:
```java
@CircuitBreaker(name = "userService", fallbackMethod = "fallbackGetUser")
public User getUser(String username) {
    return restTemplate.getForObject("http://user-service/users/" + username, User.class);
}

public User fallbackGetUser(String username, Exception e) {
    return new User(username, "Unknown", "unavailable@example.com");
}
```

### 3. Faster Detection (Configuration)

```yaml
eureka:
  instance:
    lease-renewal-interval-in-seconds: 10  # Heartbeat every 10s (default: 30)
    lease-expiration-duration-in-seconds: 30  # Evict after 30s (default: 90)
  client:
    registry-fetch-interval-seconds: 10  # Refresh cache every 10s (default: 30)
```

⚠️ **Trade-off**: More network traffic to Eureka Server

### 4. Graceful Shutdown

```yaml
server:
  shutdown: graceful

spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s
```

The service deregisters from Eureka **before** shutting down, minimizing the window.

### 5. Health Checks

```yaml
eureka:
  client:
    healthcheck:
      enabled: true
```

Eureka can check service health more actively (requires Spring Boot Actuator).

---

## Best Practice Configuration

Combine multiple strategies for maximum resilience:

```yaml
# Faster detection
eureka:
  instance:
    lease-renewal-interval-in-seconds: 10
    lease-expiration-duration-in-seconds: 30
  client:
    registry-fetch-interval-seconds: 10
    healthcheck:
      enabled: true

# Graceful shutdown
server:
  shutdown: graceful

spring:
  lifecycle:
    timeout-per-shutdown-phase: 20s
  # Retry on failure
  cloud:
    loadbalancer:
      retry:
        enabled: true
```

Plus add:
- **Circuit Breaker** for resilience
- **Fallback methods** for degraded functionality
- **Timeouts** on HTTP clients

This way, even if a service goes down, your system remains resilient with minimal user impact.

---

## Summary

- Eureka uses **local caching** - no extra request to Eureka Server per service call
- There's a **detection delay** (up to 2 minutes) when services go down
- Use **retry logic**, **circuit breakers**, **faster detection**, and **graceful shutdown** to handle failures
- The system remains **highly available** even during partial failures
