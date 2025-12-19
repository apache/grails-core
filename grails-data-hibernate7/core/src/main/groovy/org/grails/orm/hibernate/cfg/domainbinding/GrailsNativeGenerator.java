package org.grails.orm.hibernate.cfg.domainbinding;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.generator.EventType;
import org.hibernate.generator.GeneratorCreationContext;
import org.hibernate.id.NativeGenerator;
import jakarta.persistence.GenerationType;

public class GrailsNativeGenerator extends NativeGenerator {

    public GrailsNativeGenerator(GeneratorCreationContext context) {
        // This triggers the internal switch logic you provided earlier,
        // which calls setIdentity(true) on the column for H2.
        this.initialize(null, null, context);
    }

    @Override
    public Object generate(SharedSessionContractImplementor session, Object entity, Object currentValue, EventType eventType) {
        // 1. Support Grails assigned identifiers
        if (currentValue != null) {
            return currentValue;
        }

        // 2. Fix the Hibernate 7 ClassCastException
        // NativeGenerator.generate() tries to cast the delegate to BeforeExecutionGenerator.
        // If the dialect chose IDENTITY, that cast fails. We bypass it by returning null.
        if (this.getGenerationType() == GenerationType.IDENTITY) {
            return null;
        }

        // 3. For Sequences/UUIDs, delegate to the standard logic
        return super.generate(session, entity, currentValue, eventType);
    }
}