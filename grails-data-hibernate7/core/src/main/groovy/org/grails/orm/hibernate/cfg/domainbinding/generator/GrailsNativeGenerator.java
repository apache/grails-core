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
package org.grails.orm.hibernate.cfg.domainbinding.generator;

import java.io.Serial;
import java.lang.reflect.Field;

import jakarta.persistence.GenerationType;

import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.generator.EventType;
import org.hibernate.generator.GeneratorCreationContext;
import org.hibernate.id.NativeGenerator;
import org.hibernate.id.enhanced.SequenceStyleGenerator;

/**
 * A native generator that supports Grails assigned identifiers and fixes Hibernate 7 ClassCastException.
 *
 * @author Graeme Rocher
 * @since 7.0
 */
public class GrailsNativeGenerator extends NativeGenerator {

    @Serial
    private static final long serialVersionUID = 1L;

    public GrailsNativeGenerator(GeneratorCreationContext context) {
        // This triggers the internal switch logic in NativeGenerator,
        // which calls setIdentity(true) on the column for H2.
        try {
            this.initialize(null, null, context);
        } catch (Exception ignored) {
            // ignore for now, helps with testing robustness where context might be incomplete
        }
    }

    @Override
    public Object generate(
            SharedSessionContractImplementor session, Object entity, Object currentValue, EventType eventType) {
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

        // 3. Prevent NPE if configuration failed (e.g. DDL error)
        // Access private field dialectNativeGenerator in NativeGenerator
        try {
            Field field = NativeGenerator.class.getDeclaredField("dialectNativeGenerator");
            field.setAccessible(true);
            Object delegate = field.get(this);
            if (delegate instanceof SequenceStyleGenerator ssg) {
                if (ssg.getDatabaseStructure() == null) {
                    throw new HibernateException("Identifier generator (SequenceStyleGenerator) was not properly initialized. This usually happens if table creation failed (check previous logs for DDL errors).");
                }
            }
        } catch (HibernateException e) {
            throw e;
        } catch (Exception ignored) {
            // ignore reflection errors
        }

        // 4. For Sequences/UUIDs, delegate to the standard logic
        return super.generate(session, entity, null, eventType);
    }
}
