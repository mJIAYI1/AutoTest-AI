package com.autotestai.demo;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
@Tag(name = "AutoTest AI Demo")
class DemoApiController {

    private final DemoStore store;

    DemoApiController(DemoStore store) {
        this.store = store;
    }

    @GetMapping("/")
    Map<String, Object> status() {
        return Map.of(
                "application", "AutoTest AI Demo API",
                "status", "UP",
                "openapi", "/v3/api-docs",
                "intentionalBugs", List.of(
                        "missing user returns 500",
                        "negative product price is accepted",
                        "zero order quantity is accepted",
                        "invalid token returns 500"));
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a demo user")
    UserResponse register(@Valid @RequestBody RegisterRequest request) {
        DemoStore.UserRecord user = store.register(request.username(), request.password());
        return new UserResponse(user.id(), user.username());
    }

    @PostMapping("/login")
    @Operation(summary = "Log in and receive a demo bearer token")
    LoginResponse login(@Valid @RequestBody LoginRequest request) {
        DemoStore.UserRecord user = store.authenticate(request.username(), request.password());
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        return new LoginResponse(store.issueToken(user), new UserResponse(user.id(), user.username()));
    }

    @GetMapping("/users/me")
    @Operation(summary = "Read the current demo user")
    UserResponse currentUser(@RequestHeader(value = "Authorization", required = false) String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bearer token is required");
        }
        DemoStore.UserRecord user = store.findUserByToken(authorization.substring(7));
        if (user == null) {
            throw new IntentionalDemoBugException("Invalid token unexpectedly caused an internal error");
        }
        return new UserResponse(user.id(), user.username());
    }

    @GetMapping("/users/{id}")
    @Operation(summary = "Read a user; deliberately returns 500 when missing")
    UserResponse user(@PathVariable long id) {
        DemoStore.UserRecord user = store.findUser(id);
        if (user == null) {
            throw new IntentionalDemoBugException("Missing user unexpectedly caused an internal error");
        }
        return new UserResponse(user.id(), user.username());
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a product; deliberately accepts negative prices")
    ProductResponse createProduct(@Valid @RequestBody ProductRequest request) {
        DemoStore.ProductRecord product = store.createProduct(request.name(), request.price());
        return new ProductResponse(product.id(), product.name(), product.price());
    }

    @GetMapping("/products")
    @Operation(summary = "List demo products")
    List<ProductResponse> products() {
        return store.products().stream()
                .map(product -> new ProductResponse(product.id(), product.name(), product.price()))
                .toList();
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an order; deliberately accepts a zero quantity")
    OrderResponse createOrder(@Valid @RequestBody OrderRequest request) {
        DemoStore.OrderRecord order = store.createOrder(request.productId(), request.quantity());
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        }
        return toOrderResponse(order);
    }

    @GetMapping("/orders/{id}")
    @Operation(summary = "Read a demo order")
    OrderResponse order(@PathVariable long id) {
        DemoStore.OrderRecord order = store.findOrder(id);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }
        return toOrderResponse(order);
    }

    private static OrderResponse toOrderResponse(DemoStore.OrderRecord order) {
        return new OrderResponse(order.id(), order.productId(), order.quantity(), order.status());
    }

    record RegisterRequest(
            @NotBlank @Size(max = 50) String username,
            @NotBlank @Size(min = 6, max = 100) String password) {
    }

    record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    record ProductRequest(@NotBlank @Size(max = 100) String name, @NotNull BigDecimal price) {
    }

    record OrderRequest(@NotNull Long productId, @NotNull Integer quantity) {
    }

    record UserResponse(long id, String username) {
    }

    record LoginResponse(String token, UserResponse user) {
    }

    record ProductResponse(long id, String name, BigDecimal price) {
    }

    record OrderResponse(long id, long productId, int quantity, String status) {
    }
}
