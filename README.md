# Eureka Service Discovery Demo

## Running with Docker Compose

```bash
docker-compose up --build
```

This will start:
- **Eureka Server** on port 8761
- **User Service** on port 8081
- **Order Service** on port 8082

## Testing the Services

### 1. Check Eureka Dashboard
http://localhost:8761

You should see both `USER-SERVICE` and `ORDER-SERVICE` registered.

### 2. Test User Service directly
```bash
# Known users
curl http://localhost:8081/users/john
curl http://localhost:8081/users/jane
curl http://localhost:8081/users/bob

# Unknown user (returns random user)
curl http://localhost:8081/users/unknown
```

### 3. Test Order Service (calls User Service via Eureka)
```bash
# Order for known user
curl http://localhost:8082/orders/john

# Order for unknown user (gets random user)
curl http://localhost:8082/orders/alice
```

## How It Works

1. **User Service** provides user information at `/users/{username}`
   - Returns predefined users: john, jane, bob
   - Returns random user for unknown usernames

2. **Order Service** creates orders by:
   - Looking up `user-service` via Eureka (service discovery)
   - Calling User Service to get user information
   - Creating a mock order with the user details

3. **Service Discovery**: Order Service uses `http://user-service/users/{username}` instead of hardcoded IP/port. Eureka resolves this to the actual User Service instance.

## Stop Services
```bash
docker-compose down
```

## What's Happening

1. **Eureka Server** starts on port 8761 and provides the registry
2. **Client Service** starts on port 8080 and automatically registers with Eureka
3. Client sends heartbeats every 30 seconds to maintain registration
4. If client stops, Eureka will evict it after 90 seconds (default)

## Key Configuration

**Server (application.yml):**
- `register-with-eureka: false` - Server doesn't register itself
- `fetch-registry: false` - Server doesn't fetch registry from others

**Client (application.yml):**
- `spring.application.name` - Service name in registry
- `eureka.client.service-url.defaultZone` - Eureka server location
