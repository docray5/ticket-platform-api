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
    @Value("${payment.decline-rate}")
    private float declineRate;

    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = PaymentDeclinedException.class)
    @Override
    public void pay(UUID holdId, BigDecimal amount) {
        boolean declined = ThreadLocalRandom.current().nextFloat() < declineRate;

        Payment payment = new Payment(holdId,
                declined ? Payment.PaymentStatus.DECLINED : Payment.PaymentStatus.SUCCEEDED,
                amount, "mock_"+UUID.randomUUID());

        paymentRepository.save(payment);

        if (declined) {
            throw new PaymentDeclinedException();
        }
    }
}
