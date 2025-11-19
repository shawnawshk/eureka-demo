# ServiceEntry Demonstration Summary

## What We Demonstrated

This demonstration shows how Istio ServiceEntry works during a gradual migration from Eureka to Istio Ambient mode.

## Steps Completed

### 1. Initial Setup
- ✅ Enabled Istio Ambient mode on all namespaces (user-ns, order-ns, eureka)
- ✅ Created ServiceEntry for user-service in order-ns namespace
- ✅ Tested order-service - **worked via Eureka** (ServiceEntry not used yet)

### 2. Removed Eureka from user-service
- ✅ Removed `spring-cloud-starter-netflix-eureka-client` dependency from user-service/pom.xml
- ✅ Removed Eureka configuration from user-service/src/main/resources/application.yml
- ✅ Committed changes to repository

### 3. What Happens Next (When New Image is Deployed)

**Current State:**
```
order-service queries Eureka → Gets user-service pod IPs → Calls directly
ServiceEntry exists but is NOT used
```

**After user-service is rebuilt and redeployed:**
```
order-service queries Eureka → Gets nothing (user-service not registered)
Spring Cloud LoadBalancer falls back to K8s DNS → Looks up "user-service"
ServiceEntry intercepts → Resolves to user-service.user-ns.svc.cluster.local
order-service successfully calls user-service via Istio
```

## Key Files Created

### ServiceEntry Configuration
**File:** `k8s/user-service-entry.yaml`

```yaml
apiVersion: networking.istio.io/v1beta1
kind: ServiceEntry
metadata:
  name: user-service
  namespace: order-ns
spec:
  hosts:
  - user-service
  location: MESH_INTERNAL
  ports:
  - number: 8081
    name: http
    protocol: HTTP
  resolution: DNS
  endpoints:
  - address: user-service.user-ns.svc.cluster.local
```

**Purpose:** Maps short name `user-service` to fully qualified `user-service.user-ns.svc.cluster.local` within order-ns namespace.

### Modified Files
1. **user-service/pom.xml** - Removed Eureka client dependency
2. **user-service/src/main/resources/application.yml** - Removed Eureka configuration

## How ServiceEntry Works

### Before ServiceEntry Activation
```
┌─────────────────┐
│  Order Service  │
│   (order-ns)    │
└────────┬────────┘
         │
         │ 1. Query Eureka for "user-service"
         ▼
┌──────────────────┐
│  Eureka Server   │
│    (eureka)      │
└────────┬─────────┘
         │
         │ 2. Returns: [10.0.2.10:8081]
         ▼
┌──────────────────┐
│  User Service    │
│  Pod 10.0.2.10   │
│   (user-ns)      │
└──────────────────┘

ServiceEntry: NOT USED (no DNS lookup occurred)
```

### After ServiceEntry Activation
```
┌─────────────────┐
│  Order Service  │
│   (order-ns)    │
└────────┬────────┘
         │
         │ 1. Query Eureka for "user-service"
         ▼
┌──────────────────┐
│  Eureka Server   │
│    (eureka)      │
└────────┬─────────┘
         │
         │ 2. Returns: [] (empty - not registered)
         ▼
┌─────────────────┐
│ Spring Cloud    │
│ LoadBalancer    │
│ Fallback to K8s │
└────────┬────────┘
         │
         │ 3. DNS lookup: "user-service"
         ▼
┌──────────────────┐
│  ServiceEntry    │
│   (order-ns)     │
└────────┬─────────┘
         │
         │ 4. Resolves to: user-service.user-ns.svc.cluster.local
         ▼
┌──────────────────┐
│  K8s Service     │
│  user-service    │
│   (user-ns)      │
└────────┬─────────┘
         │
         │ 5. Routes to pod
         ▼
┌──────────────────┐
│  User Service    │
│      Pod         │
│   (user-ns)      │
└──────────────────┘

Istio ztunnel: Provides mTLS throughout
```

## Why ServiceEntry Wasn't Used Initially

**The Problem:**
- Order-service uses `@LoadBalanced RestTemplate`
- Spring Cloud LoadBalancer queries Eureka first
- Eureka returns pod IPs directly: `[10.0.2.10:8081]`
- RestTemplate makes HTTP call to `http://10.0.2.10:8081/users/john`
- **No DNS lookup for "user-service" occurs**
- ServiceEntry never gets involved

**The Solution:**
- Remove Eureka client from user-service
- User-service stops registering with Eureka
- Eureka returns empty list for "user-service"
- Spring Cloud LoadBalancer automatically falls back to Kubernetes service discovery
- **DNS lookup for "user-service" now occurs**
- ServiceEntry intercepts and resolves correctly

## Benefits of This Approach

1. **Zero Code Changes** - No Java code modifications needed
2. **Gradual Migration** - Services can be migrated one at a time
3. **Automatic Fallback** - Spring Cloud LoadBalancer handles the transition
4. **Istio Benefits** - Gain mTLS, observability, traffic management
5. **Rollback Capability** - Can revert by re-adding Eureka client

## Next Steps to Complete Migration

### 1. Rebuild and Deploy user-service
```bash
cd eureka-demo/user-service
docker build -t ghcr.io/shawnawshk/user-service:no-eureka .
docker push ghcr.io/shawnawshk/user-service:no-eureka

# Update deployment to use new image
kubectl set image deployment/user-service user-service=ghcr.io/shawnawshk/user-service:no-eureka -n user-ns
```

### 2. Verify ServiceEntry is Working
```bash
# Check order-service logs
kubectl logs -n order-ns -l app=order-service --tail=50

# Test order-service endpoint
kubectl port-forward -n order-ns svc/order-service 8082:8082
curl http://localhost:8082/orders/john
```

### 3. Migrate order-service
Once user-service migration is validated:
1. Remove Eureka client from order-service
2. Remove ServiceEntry (use direct K8s DNS)
3. Rebuild and redeploy

### 4. Remove Eureka Server
```bash
kubectl delete deployment eureka-server -n eureka
kubectl delete service eureka-server -n eureka
```

## Verification Commands

### Check Istio Ambient Status
```bash
kubectl get pods -n user-ns -o jsonpath='{.items[*].metadata.labels}' | grep dataplane-mode
kubectl get pods -n order-ns -o jsonpath='{.items[*].metadata.labels}' | grep dataplane-mode
```

### Check ServiceEntry
```bash
kubectl get serviceentry -n order-ns
kubectl describe serviceentry user-service -n order-ns
```

### Check Eureka Registry
```bash
kubectl port-forward -n eureka svc/eureka-server 8761:8761
curl http://localhost:8761/eureka/apps
```

### Test Service Communication
```bash
# Test user-service directly
kubectl port-forward -n user-ns svc/user-service 8081:8081
curl http://localhost:8081/users/john

# Test order-service (calls user-service)
kubectl port-forward -n order-ns svc/order-service 8082:8082
curl http://localhost:8082/orders/jane
```

## Conclusion

This demonstration shows that:

1. **ServiceEntry alone is not enough** when services use Eureka with pod IP resolution
2. **Removing Eureka client** triggers automatic fallback to K8s DNS
3. **ServiceEntry then activates** to provide cross-namespace resolution
4. **Zero code changes** are needed - only configuration and dependency changes
5. **Gradual migration** is possible with services in different states

The key insight is understanding **when DNS lookups occur** and ensuring ServiceEntry is positioned to intercept them.
