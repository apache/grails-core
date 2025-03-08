/* 
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.orm.hibernate.cfg

import groovy.transform.AutoClone
import groovy.transform.CompileStatic
import groovy.transform.builder.Builder
import groovy.transform.builder.SimpleStrategy

/**
 * Represents a Join table in Grails mapping. It has a name which represents the name of the table, a key
 * for the primary key and a column which is the other side of the join.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@AutoClone
@Builder(builderStrategy = SimpleStrategy, prefix = '')
@CompileStatic
class JoinTable extends Table {
    /**
     * The foreign key column
     */
    ColumnConfig key
    /**
     * The child id column
     */
    ColumnConfig column

    /**
     * Configures the column
     * @param columnConfig The column config
     * @return This join table config
     */
    JoinTable key(@DelegatesTo(ColumnConfig) Closure columnConfig) {
        key = ColumnConfig.configureNew(columnConfig)
        return this
    }
    /**
     * Configures the column
     * @param columnConfig The column config
     * @return This join table config
     */
    JoinTable column(@DelegatesTo(ColumnConfig) Closure columnConfig) {
        column = ColumnConfig.configureNew(columnConfig)
        return this
    }

    /**
     * Configures the column
     * @param columnName the column name
     * @return This join table config
     */
    JoinTable key(String columnName) {
        key = new ColumnConfig(name: columnName)
        return this
    }

    /**
     * Configures the column
     * @param columnName the column name
     * @return This join table config
     */
    JoinTable column(String columnName) {
        column = new ColumnConfig(name: columnName)
        return this
    }
}
