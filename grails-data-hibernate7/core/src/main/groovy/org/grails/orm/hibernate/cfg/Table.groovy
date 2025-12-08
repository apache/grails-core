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
import org.springframework.beans.MutablePropertyValues
import org.springframework.validation.DataBinder

/**
 * Represents a table definition in GORM.
 *
 * @author Graeme Rocher
 * @since 1.1
 */
@Builder(builderStrategy = SimpleStrategy, prefix = '')
@CompileStatic
class Table {
    /**
     * The table name
     */
    String name
    /**
     * The table catalog
     */
    String catalog
    /**
     * The table schema
     */
    String schema

    /**
     * Configures a new Table instance
     *
     * @param config The configuration
     * @return The new instance
     */
    static Table configureNew(@DelegatesTo(Table) Closure config) {
        Table table = new Table()
        return configureExisting(table, config)
    }

    /**
     * Configures an existing Table instance
     *
     * @param config The configuration
     * @return The new instance
     */
    static Table configureExisting(Table table, Map config) {
        DataBinder dataBinder = new DataBinder(table)
        dataBinder.bind(new MutablePropertyValues(config))
        return table
    }
    /**
     * Configures an existing PropertyConfig instance
     *
     * @param config The configuration
     * @return The new instance
     */
    static Table configureExisting(Table table, @DelegatesTo(Table) Closure config) {
        config.setDelegate(table)
        config.setResolveStrategy(Closure.DELEGATE_ONLY)
        config.call()
        return table
    }
}
