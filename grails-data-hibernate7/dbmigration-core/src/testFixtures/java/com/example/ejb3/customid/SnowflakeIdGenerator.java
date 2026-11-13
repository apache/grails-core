package com.example.ejb3.customid;

import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.IdentifierGenerator;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Custom ID generator implementing the legacy {@link IdentifierGenerator} interface.
 * <p>
 * This test generator simply returns incrementing {@code long} values using an in-memory
 * counter and does not interact with any {@code GeneratorCreationContext} or its properties.
 */
public class SnowflakeIdGenerator implements IdentifierGenerator {

    private static final AtomicLong counter = new AtomicLong(1);

    @Override
    public Serializable generate(SharedSessionContractImplementor session, Object object) throws HibernateException {
        return counter.getAndIncrement();
    }
}
