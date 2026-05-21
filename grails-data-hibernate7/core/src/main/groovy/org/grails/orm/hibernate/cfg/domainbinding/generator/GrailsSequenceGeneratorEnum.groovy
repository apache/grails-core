/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.grails.orm.hibernate.cfg.domainbinding.generator

import groovy.transform.CompileStatic

import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment
import org.hibernate.generator.Assigned
import org.hibernate.generator.Generator
import org.hibernate.generator.GeneratorCreationContext
import org.hibernate.id.uuid.UuidGenerator

import org.grails.orm.hibernate.cfg.HibernateSimpleIdentity
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity

/**
 * Enum for Grails ID generator strategies.
 */
@CompileStatic
enum GrailsSequenceGeneratorEnum {

    IDENTITY('identity'),
    SEQUENCE('sequence'),
    SEQUENCE_IDENTITY('sequence-identity'),
    INCREMENT('increment'),
    UUID('uuid'),
    UUID2('uuid2'),
    ASSIGNED('assigned'),
    TABLE('table'),
    ENHANCED_TABLE('enhanced-table'),
    HILO('hilo'),
    NATIVE('native')

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

    protected static Generator getGenerator(
            String name,
            GeneratorCreationContext context,
            HibernateSimpleIdentity mappedId,
            GrailsHibernatePersistentEntity domainClass,
            JdbcEnvironment jdbcEnvironment,
            PersistentEntityNamingStrategy namingStrategy) {
        return getGenerator(fromName(name).orElse(NATIVE), context, mappedId, domainClass, jdbcEnvironment, namingStrategy)
    }

    static Generator getGenerator(
            GrailsSequenceGeneratorEnum sequenceGeneratorEnum,
            GeneratorCreationContext context,
            HibernateSimpleIdentity mappedId,
            GrailsHibernatePersistentEntity domainClass,
            JdbcEnvironment jdbcEnvironment,
            PersistentEntityNamingStrategy namingStrategy) {
        switch (sequenceGeneratorEnum) {
            case IDENTITY:
                return new GrailsIdentityGenerator(context, mappedId)
            case [SEQUENCE, SEQUENCE_IDENTITY, HILO]:
                return new GrailsSequenceStyleGenerator(context, mappedId, jdbcEnvironment)
            case INCREMENT:
                return new GrailsIncrementGenerator(context, mappedId, domainClass, namingStrategy)
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
