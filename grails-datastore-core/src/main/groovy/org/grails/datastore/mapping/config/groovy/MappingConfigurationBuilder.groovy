/*
 * Copyright 2015 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.datastore.mapping.config.groovy

import org.grails.datastore.mapping.config.Entity
import org.grails.datastore.mapping.config.Property


/**
 * Interface for objects that build a mapping configuration
 *
 * @author Graeme Rocher
 * @since 5.0
 *
 */
interface MappingConfigurationBuilder<E extends Entity, P extends Property> {

    /**
     * Evaluates the configuration for the given closure
     *
     * @param mappingClosure The closure configuration
     *
     * @return The mapping object
     */
    E evaluate(Closure mappingClosure)
    /**
     * Evaluates the configuration for the given closure
     *
     * @param mappingClosure The closure configuration
     * @param context A context object
     *
     * @return The mapping object
     */
    E evaluate(Closure mappingClosure, Object context)

    /**
     * @return The build property definitions
     */
    Map<String, P> getProperties()
}