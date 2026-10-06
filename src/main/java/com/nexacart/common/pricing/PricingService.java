package com.nexacart.common.pricing;

import com.nexacart.config.NexaCartProperties;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class PricingService {

    private static final int MONEY_SCALE = 2;

    private final NexaCartProperties properties;

    public PricingService(NexaCartProperties properties) {
        this.properties = properties;
    }

    public BigDecimal normalize(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    public BigDecimal lineTotal(BigDecimal unitPrice, int quantity) {
        return normalize(unitPrice.multiply(BigDecimal.valueOf(quantity)));
    }

    public BigDecimal shippingFor(BigDecimal subtotal) {
        if (subtotal.signum() <= 0) {
            return money(BigDecimal.ZERO);
        }
        BigDecimal threshold = properties.getOrders().getFreeShippingThreshold();
        if (threshold != null && subtotal.compareTo(threshold) >= 0) {
            return money(BigDecimal.ZERO);
        }
        return money(properties.getOrders().getShippingFee());
    }

    public BigDecimal total(BigDecimal subtotal, BigDecimal shipping, BigDecimal discount) {
        BigDecimal total = subtotal.add(shipping).subtract(discount);
        if (total.signum() < 0) {
            total = BigDecimal.ZERO;
        }
        return normalize(total);
    }

    public String currency() {
        return properties.getOrders().getCurrency();
    }

    private BigDecimal money(BigDecimal value) {
        return normalize(value.max(BigDecimal.ZERO));
    }
}
