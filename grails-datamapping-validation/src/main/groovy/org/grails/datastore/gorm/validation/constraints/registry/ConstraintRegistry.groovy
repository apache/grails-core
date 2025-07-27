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

package org.grails.datastore.gorm.validation.constraints.registry

import grails.gorm.validation.Constraint
import org.grails.datastore.gorm.validation.constraints.factory.ConstraintFactory

/**
 * A registry of Constraint factories
 *
 * @author Graeme Rocher
 * @since 6.0
 */
interface ConstraintRegistry {

    String DEFAULT_CONSTRAINTS = 'grails.gorm.default.constraints'

    /**
     * Adds a constraint factory
     *
     * @param name The name of the constraint (example: nullable, blank etc.)
     * @param constraintFactory The constraint factory
     */
    void addConstraintFactory(ConstraintFactory constraintFactory)

    /**
     * Adds a constraint for the given class
     *
     * @param constraintClass The constraint class
     * @param targetPropertyTypes the target types if any
     */
    void addConstraint(Class<? extends Constraint> constraintClass, List<Class> targetPropertyTypes)

    /**
     * Adds a constraint for the given class
     *
     * @param constraintClass The constraint class
     */
    void addConstraint(Class<? extends Constraint> constraintClass)

    /**
     * Finds a named constraint
     *
     * @param name The short name of the constraint
     * @return The constraint
     */
    List<ConstraintFactory> findConstraintFactories(String name)

    /**
     * Finds a constraint by class
     *
     * @param name The short name of the constraint
     * @return The constraint
     */
    <T extends Constraint> List<ConstraintFactory<T>> findConstraintFactories(Class<T> constraintType)
}
