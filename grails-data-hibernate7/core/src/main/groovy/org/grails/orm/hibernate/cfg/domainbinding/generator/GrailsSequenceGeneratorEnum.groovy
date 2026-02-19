package org.grails.orm.hibernate.cfg.domainbinding.generator


import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment
import org.hibernate.generator.Assigned
import org.hibernate.generator.Generator
import org.hibernate.generator.GeneratorCreationContext
import org.hibernate.id.uuid.UuidGenerator

import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.Identity

/**
 * Enum for Grails ID generator strategies.
 */
enum GrailsSequenceGeneratorEnum {
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
    NATIVE("native")

    private final String name

    GrailsSequenceGeneratorEnum(String name) {
        this.name = name
    }

    String getName() {
        return name
    }

    @Override
    String toString() {
        return name
    }

    static Optional<GrailsSequenceGeneratorEnum> fromName(String name) {
        return Optional.ofNullable(values().find { it.name == name })
    }

    static Generator getGenerator(
            String name,
            GeneratorCreationContext context,
            Identity mappedId,
            GrailsHibernatePersistentEntity domainClass,
            JdbcEnvironment jdbcEnvironment) {
        return getGenerator(fromName(name).orElse(NATIVE), context, mappedId, domainClass, jdbcEnvironment)
    }

    static Generator getGenerator(
            GrailsSequenceGeneratorEnum sequenceGeneratorEnum,
            GeneratorCreationContext context,
            Identity mappedId,
            GrailsHibernatePersistentEntity domainClass,
            JdbcEnvironment jdbcEnvironment) {
        switch (sequenceGeneratorEnum) {
            case IDENTITY:
                return new GrailsIdentityGenerator(context, mappedId)
            case [SEQUENCE, SEQUENCE_IDENTITY, HILO]:
                return new GrailsSequenceStyleGenerator(context, mappedId, jdbcEnvironment)
            case INCREMENT:
                return new GrailsIncrementGenerator(context, mappedId, domainClass)
            case [UUID, UUID2]:
                return new UuidGenerator(context.getType().getReturnedClass())
            case ASSIGNED:
                return new Assigned()
            case [TABLE, ENHANCED_TABLE]:
                return new GrailsTableGenerator(context, mappedId, jdbcEnvironment)
            case NATIVE:
                return new GrailsNativeGenerator(context)
            default:
                return new GrailsNativeGenerator(context)
        }
    }
}