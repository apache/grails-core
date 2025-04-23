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

package org.grails.datastore.gorm.validation.constraints.eval

import grails.gorm.validation.ConstrainedProperty
import org.grails.datastore.gorm.validation.constraints.builder.ConstrainedPropertyBuilder
import org.grails.datastore.mapping.model.config.GormProperties

/**
 * Evaluates Constraints for a GORM entity
 *
 * @author Graeme Rocher
 * @since 6.0
 */
interface ConstraintsEvaluator {
    /**
     * The name of the constraints property
     */
    String PROPERTY_NAME = GormProperties.CONSTRAINTS
    /**
     * The default constraints to use
     * @return A map of default constraints
     */
    Map<String, Object> getDefaultConstraints()

    /**
     * Evaluate constraints for the given class
     *
     * @param cls The class to evaluate constraints for
     * @return A map of constrained properties
     */
    Map<String, ConstrainedProperty> evaluate(@SuppressWarnings("rawtypes") Class cls)

    /**
     * Evaluate constraints for the given class
     *
     * @param cls The class to evaluate constraints for
     * @oaram defaultNullable Whether to default to allow nullable
     * @return A map of constrained properties
     */
    Map<String, ConstrainedProperty> evaluate(@SuppressWarnings("rawtypes") Class cls, boolean defaultNullable)

    /**
     * Evaluate constraints for the given class
     *
     * @param cls The class to evaluate constraints for
     * @param defaultNullable indicates if properties are nullable by default
     * @param useOnlyAdHocConstraints indicates if evaluating without pre-declared constraints
     * @param adHocConstraintsClosures ad-hoc constraints to evaluate for
     * @return A map of constrained properties
     */
    Map<String, ConstrainedProperty> evaluate(Class<?> cls, boolean defaultNullable, boolean useOnlyAdHocConstraints, Closure... adHocConstraintsClosures)

    /**
     * Obtains a new builder from this evaluator
     *
     * @param theClass The class
     * @return The builder
     */
    ConstrainedPropertyBuilder newConstrainedPropertyBuilder(Class<?> theClass)
}