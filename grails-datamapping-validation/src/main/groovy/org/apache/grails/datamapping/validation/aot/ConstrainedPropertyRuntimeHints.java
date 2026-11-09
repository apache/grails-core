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
package org.apache.grails.datamapping.validation.aot;

import org.jspecify.annotations.Nullable;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/**
 * Registers the constrained property a domain class's constraints are applied to.
 *
 * <p>A constraint that names no registered {@code Constraint} is applied by setting a property of
 * the same name, through Groovy rather than by a call the compiler can see:
 * {@code ((GroovyObject) this).setProperty(constraintName, constrainingValue)}. So
 * {@code password: true} is a reflective invocation of {@code setPassword(boolean)}, and an image
 * keeps only the members something asks for.</p>
 *
 * <p>Without these, an application starts and then fails the first time a domain instance is
 * validated, naming a setter nobody wrote:</p>
 *
 * <pre>
 * MissingReflectionRegistrationError: Cannot reflectively invoke method
 * 'public void grails.gorm.validation.DefaultConstrainedProperty.setPassword(boolean)'
 * </pre>
 *
 * <p>Registered here rather than left to a tracing agent because an agent records only the
 * constraints the run it watched happened to evaluate. Validation is lazy -- the evaluator is built
 * the first time something is validated -- so an application whose data already exists never
 * validates during tracing and records nothing, and the same image then fails against an empty
 * database. Which of the two an image was traced against is not a property anyone would think to
 * keep constant.</p>
 *
 * @since 8.0
 */
public class ConstrainedPropertyRuntimeHints implements RuntimeHintsRegistrar {

    /**
     * The types a constraint is set on. Named as strings, and registered only when present, so this
     * stays correct for an application that does not have every one of them on its classpath.
     */
    private static final String[] CONSTRAINED_TYPES = {
        "grails.gorm.validation.DefaultConstrainedProperty",
        "grails.gorm.validation.ConstrainedProperty",
        "org.grails.datastore.gorm.validation.constraints.builder.ConstrainedPropertyBuilder"
    };

    @Override
    public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
        for (String type : CONSTRAINED_TYPES) {
            hints.reflection().registerTypeIfPresent(classLoader, type,
                    MemberCategory.INVOKE_DECLARED_METHODS,
                    MemberCategory.INVOKE_PUBLIC_METHODS,
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
        }
    }

}
