package com.bilicki.ticketing.payment;

import java.math.BigDecimal;
import java.util.UUID;

public interface PaymentFacade {
    void pay(UUID holdId, BigDecimal amount);
}
