/*
 * Copyright 2003-2007 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.orm.hibernate.cfg

import groovy.transform.CompileStatic
import groovy.transform.builder.Builder
import groovy.transform.builder.SimpleStrategy
import org.grails.datastore.mapping.config.Property
import org.springframework.beans.MutablePropertyValues
import org.springframework.validation.DataBinder

/**
 * Defines the identity generation strategy. In the case of a 'composite' identity the properties
 * array defines the property names that formulate the composite id.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
@Builder(builderStrategy = SimpleStrategy, prefix = '')
class Identity extends Property {
    /**
     * The generator to use
     */
    String generator = 'native'
    /**
     * The column to map to
     */
    String column = 'id'
    /**
     * The name of the id property
     */
    String name
    /**
     * The natural id definition
     */
    NaturalId natural
    /**
     * The type
     */
    Class type = Long
    /**
     * Any parameters (for example for the generator)
     */
    Map params = [:]

    /**
     * Define the natural id
     * @param naturalIdDef The callable
     * @return This id
     */
    Identity naturalId(@DelegatesTo(NaturalId) Closure naturalIdDef) {
        naturalIdDef.setDelegate(new NaturalId())
        naturalIdDef.setResolveStrategy(Closure.DELEGATE_ONLY)
        naturalIdDef.call()
        return this
    }

    String toString() { "id[generator:$generator, column:$column, type:$type]" }

    /**
     * Configures a new Identity instance
     *
     * @param config The configuration
     * @return The new instance
     */
    static Identity configureNew(@DelegatesTo(Identity) Closure config) {
        Identity property = new Identity()
        return configureExisting(property, config)
    }

    /**
     * Configures an existing Identity instance
     *
     * @param config The configuration
     * @return The new instance
     */
    static Identity configureExisting(Identity property, Map config) {
        DataBinder dataBinder = new DataBinder(property)
        dataBinder.bind(new MutablePropertyValues(config))
        return property
    }
    /**
     * Configures an existing PropertyConfig instance
     *
     * @param config The configuration
     * @return The new instance
     */
    static Identity configureExisting(Identity property, @DelegatesTo(Identity) Closure config) {
        config.setDelegate(property)
        config.setResolveStrategy(Closure.DELEGATE_ONLY)
        config.call()
        return property
    }
}
