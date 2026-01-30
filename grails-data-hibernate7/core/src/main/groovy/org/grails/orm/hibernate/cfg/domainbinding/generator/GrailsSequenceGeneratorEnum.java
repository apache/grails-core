package org.grails.orm.hibernate.cfg.domainbinding.generator;

import java.util.Arrays;
import java.util.Optional;

import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.hibernate.generator.Assigned;
import org.hibernate.generator.Generator;
import org.hibernate.generator.GeneratorCreationContext;
import org.hibernate.id.uuid.UuidGenerator;

import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.Identity;
import org.grails.orm.hibernate.cfg.domainbinding.GrailsIdentityGenerator;
import org.grails.orm.hibernate.cfg.domainbinding.GrailsIncrementGenerator;
import org.grails.orm.hibernate.cfg.domainbinding.GrailsNativeGenerator;
import org.grails.orm.hibernate.cfg.domainbinding.GrailsSequenceStyleGenerator;
import org.grails.orm.hibernate.cfg.domainbinding.GrailsTableGenerator;

/**
 * Enum for Grails ID generator strategies.
 */
public enum GrailsSequenceGeneratorEnum {
    IDENTITY("identity"),
    SEQUENCE("sequence"),
    SEQUENCE_IDENTITY("sequence-identity"),
    INCREMENT("increment"),
    UUID("uuid"),
    UUID2("uuid2"),
    ASSIGNED("assigned"),
    TABLE("table"),
    ENHANCED_TABLE("enhanced-table"),
    HILO("hilo"),
    NATIVE("native");

    private final String name;

    GrailsSequenceGeneratorEnum(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }

    public static Optional<GrailsSequenceGeneratorEnum> fromName(String name) {
        return Arrays.stream(values())
                .filter(e -> e.name.equals(name))
                .findFirst();
    }

    public static Generator getGenerator(
            String name,
            GeneratorCreationContext context,
            Identity mappedId,
            GrailsHibernatePersistentEntity domainClass,
            JdbcEnvironment jdbcEnvironment) {
        return getGenerator(fromName(name).orElse(NATIVE), context, mappedId, domainClass, jdbcEnvironment);
    }

    public static Generator getGenerator(
            GrailsSequenceGeneratorEnum sequenceGeneratorEnum,
            GeneratorCreationContext context,
            Identity mappedId,
            GrailsHibernatePersistentEntity domainClass,
            JdbcEnvironment jdbcEnvironment) {
        switch (sequenceGeneratorEnum) {
            case IDENTITY:
                return new GrailsIdentityGenerator(context, mappedId);
            case SEQUENCE:
            case SEQUENCE_IDENTITY:
            case HILO:
                return new GrailsSequenceStyleGenerator(context, mappedId, jdbcEnvironment);
            case INCREMENT:
                return new GrailsIncrementGenerator(context, mappedId, domainClass);
            case UUID:
            case UUID2:
                return new UuidGenerator(context.getType().getReturnedClass());
            case ASSIGNED:
                return new Assigned();
            case TABLE:
            case ENHANCED_TABLE:
                return new GrailsTableGenerator(context, mappedId, jdbcEnvironment);
            case NATIVE:
            default:
                return new GrailsNativeGenerator(context);
        }
    }
}
