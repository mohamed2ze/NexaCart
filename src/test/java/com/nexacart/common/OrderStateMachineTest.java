package com.nexacart.common;

import com.nexacart.common.exception.ApiException;
import com.nexacart.order.entity.OrderStatus;
import com.nexacart.order.service.OrderStateMachine;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderStateMachineTest {

    @Test
    void allowsValidForwardTransitions() {
        assertThat(OrderStateMachine.canTransition(OrderStatus.PENDING, OrderStatus.CONFIRMED)).isTrue();
        assertThat(OrderStateMachine.canTransition(OrderStatus.CONFIRMED, OrderStatus.PROCESSING)).isTrue();
        assertThat(OrderStateMachine.canTransition(OrderStatus.PROCESSING, OrderStatus.SHIPPED)).isTrue();
        assertThat(OrderStateMachine.canTransition(OrderStatus.SHIPPED, OrderStatus.DELIVERED)).isTrue();
    }

    @Test
    void allowsCancellationOnlyBeforeShipping() {
        assertThat(OrderStateMachine.canTransition(OrderStatus.PENDING, OrderStatus.CANCELLED)).isTrue();
        assertThat(OrderStateMachine.canTransition(OrderStatus.CONFIRMED, OrderStatus.CANCELLED)).isTrue();
        assertThat(OrderStateMachine.canTransition(OrderStatus.PROCESSING, OrderStatus.CANCELLED)).isTrue();
        assertThat(OrderStateMachine.canTransition(OrderStatus.SHIPPED, OrderStatus.CANCELLED)).isFalse();
        assertThat(OrderStateMachine.canTransition(OrderStatus.DELIVERED, OrderStatus.CANCELLED)).isFalse();
    }

    @Test
    void rejectsSkippedAndBackwardTransitions() {
        assertThat(OrderStateMachine.canTransition(OrderStatus.PENDING, OrderStatus.SHIPPED)).isFalse();
        assertThat(OrderStateMachine.canTransition(OrderStatus.DELIVERED, OrderStatus.PROCESSING)).isFalse();
        assertThat(OrderStateMachine.canTransition(OrderStatus.CANCELLED, OrderStatus.CONFIRMED)).isFalse();
    }

    @Test
    void throwsConflictForInvalidTransition() {
        assertThatThrownBy(() -> OrderStateMachine.requireTransition(
                OrderStatus.DELIVERED,
                OrderStatus.CANCELLED
        ))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("cannot change");
    }
}
