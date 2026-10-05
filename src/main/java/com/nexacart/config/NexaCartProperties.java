package com.nexacart.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "nexacart")
public class NexaCartProperties {

    private Security security = new Security();
    private Cors cors = new Cors();
    private Orders orders = new Orders();
    private Payments payments = new Payments();
    private RateLimit rateLimit = new RateLimit();
    private Seed seed = new Seed();

    @Getter
    @Setter
    public static class Security {
        private Jwt jwt = new Jwt();
    }

    @Getter
    @Setter
    public static class Jwt {
        private String secret = "";
        private String issuer = "nexacart";
        private long accessTokenTtlSeconds = 900;
    }

    @Getter
    @Setter
    public static class Cors {
        private List<String> allowedOrigins = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class Orders {
        private BigDecimal shippingFee = new BigDecimal("5.99");
        private BigDecimal freeShippingThreshold = new BigDecimal("100.00");
        private String currency = "USD";
    }

    @Getter
    @Setter
    public static class Payments {
        private boolean simulatorEnabled = false;
    }

    @Getter
    @Setter
    public static class RateLimit {
        private boolean enabled = true;
        private int maxAttempts = 10;
        private int windowSeconds = 60;
    }

    @Getter
    @Setter
    public static class Seed {
        private boolean enabled = false;
        private String adminEmail = "admin@nexacart.local";
        private String adminPassword = "";
        private String customerPassword = "";
    }
}
