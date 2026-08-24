package com.autotestai.demo;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

@Component
class DemoStore {

    private final AtomicLong userSequence = new AtomicLong();
    private final AtomicLong productSequence = new AtomicLong();
    private final AtomicLong orderSequence = new AtomicLong();
    private final Map<Long, UserRecord> users = new ConcurrentHashMap<>();
    private final Map<Long, ProductRecord> products = new ConcurrentHashMap<>();
    private final Map<Long, OrderRecord> orders = new ConcurrentHashMap<>();
    private final Map<String, Long> tokens = new ConcurrentHashMap<>();

    DemoStore() {
        register("demo", "demo123456");
        createProduct("Demo Keyboard", new BigDecimal("199.00"));
    }

    synchronized UserRecord register(String username, String password) {
        if (users.values().stream().anyMatch(user -> user.username().equalsIgnoreCase(username))) {
            throw new DemoConflictException("Username already exists");
        }
        long id = userSequence.incrementAndGet();
        UserRecord user = new UserRecord(id, username, password);
        users.put(id, user);
        return user;
    }

    UserRecord authenticate(String username, String password) {
        return users.values().stream()
                .filter(user -> user.username().equals(username) && user.password().equals(password))
                .findFirst()
                .orElse(null);
    }

    String issueToken(UserRecord user) {
        String token = "demo-token-" + user.id();
        tokens.put(token, user.id());
        return token;
    }

    UserRecord findUserByToken(String token) {
        Long userId = tokens.get(token);
        return userId == null ? null : users.get(userId);
    }

    UserRecord findUser(long id) {
        return users.get(id);
    }

    ProductRecord createProduct(String name, BigDecimal price) {
        long id = productSequence.incrementAndGet();
        ProductRecord product = new ProductRecord(id, name, price);
        products.put(id, product);
        return product;
    }

    List<ProductRecord> products() {
        return products.values().stream().sorted((left, right) -> Long.compare(left.id(), right.id())).toList();
    }

    OrderRecord createOrder(long productId, int quantity) {
        ProductRecord product = products.get(productId);
        if (product == null) {
            return null;
        }
        long id = orderSequence.incrementAndGet();
        OrderRecord order = new OrderRecord(id, productId, quantity, "CREATED");
        orders.put(id, order);
        return order;
    }

    OrderRecord findOrder(long id) {
        return orders.get(id);
    }

    record UserRecord(long id, String username, String password) {
    }

    record ProductRecord(long id, String name, BigDecimal price) {
    }

    record OrderRecord(long id, long productId, int quantity, String status) {
    }
}
