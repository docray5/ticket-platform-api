package com.bilicki.ticketing.payment;

import com.bilicki.ticketing.payment.internal.Payment;
import com.bilicki.ticketing.payment.internal.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class PaymentFacadeImpl implements PaymentFacade {
    private final PaymentRepository paymentRepository;
    @Value("${payment.decline-rate}$")
    private float declineRate;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Override
    public void pay(UUID holdId, BigDecimal amount) {
        Payment payment = new Payment(holdId, Payment.PaymentStatus.DECLINED, amount, "mock_"+UUID.randomUUID());

        if (ThreadLocalRandom.current().nextFloat() <= declineRate) {
            paymentRepository.save(payment);
            throw new PaymentDeclinedException();
        }

        payment.setStatus(Payment.PaymentStatus.SUCCEEDED);
        paymentRepository.save(payment);
    }
}
