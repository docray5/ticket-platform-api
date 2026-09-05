package com.bilicki.ticketing.payment;

import com.bilicki.ticketing.common.DomainException;
import org.springframework.http.HttpStatus;

public class PaymentDeclinedException extends DomainException {
    public PaymentDeclinedException() {
        super(
                HttpStatus.PAYMENT_REQUIRED,
                "payment-declined",
                "Payment Declined",
                "Payment Declined"
        );
    }
}
