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

import groovy.transform.AutoClone
import groovy.transform.CompileStatic
import groovy.transform.builder.Builder
import groovy.transform.builder.SimpleStrategy
import org.springframework.beans.MutablePropertyValues
import org.springframework.validation.DataBinder

/**
 * Defines a column within the mapping.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@AutoClone
@CompileStatic
@Builder(builderStrategy = SimpleStrategy, prefix = '')
class ColumnConfig {

    /**
     * The column name
     */
    String name
    /**
     * The SQL type
     */
    String sqlType
    /**
     * The enum type
     */
    String enumType = 'default'
    /**
     * The index, can be either a boolean or a string for the name of the index
     */
    def index

    /**
     * Parses the index field when stored as a Groovy-style string literal.
     * Expected format: [column:item_idx, type:integer] or column:item_idx, type:integer
     * Returns an empty map if parsing fails or the value is invalid.
     * Throws IllegalArgumentException only if the format is clearly broken (fail-fast for bad developer input).
     */
    Map<String, String> getIndexAsMap() {
        Object raw = this.index
        if (raw == null) return [:]

        if (raw instanceof Map) {
            // Already a map → return as-is (though unlikely)
            return raw.collectEntries { k, v -> [k.toString(), v.toString()] } as Map<String, String>
        }

        if (!(raw instanceof String)) {
            // If it's a closure or something else, we can't parse it as a string map.
            // Let the caller handle other types (like closures).
            return [:]
        }
        String rawStr = raw.toString()

        String content = rawStr.trim()

        // Remove surrounding [ ] if present
        if (content.startsWith('[') && content.endsWith(']')) {
            content = content.substring(1, content.length() - 1).trim()
        }

        if (!content) return [:]

        Map<String, String> result = [:]

        // Split on top-level commas (simple heuristic: assume no commas inside values)
        content.split(',').each { pair ->
            def trimmed = pair.trim()
            if (!trimmed) return

            def kv = trimmed.split(':', 2)
            if (kv.length != 2) {
                // If it's the only pair and doesn't have a colon, treat it as the column name
                if (content == trimmed && !content.contains(',')) {
                    result['column'] = content
                    return
                }
                // Invalid pair → fail fast (developer mistake)
                throw new IllegalArgumentException(
                        "Invalid index pair format '$trimmed' in string: '$raw'"
                )
            }

            String key = kv[0].trim()
            String value = kv[1].trim()

            // Strip surrounding quotes from value if present
            if ((value.startsWith("'") && value.endsWith("'")) ||
                    (value.startsWith('"') && value.endsWith('"'))) {
                value = value.substring(1, value.length() - 1)
            }

            result[key] = value
        }

        if (result.isEmpty()) {
            throw new IllegalArgumentException("No valid key:value pairs found in index string: '$raw'")
        }

        return result
    }
    /**
     * Whether the column is unique
     */
    def unique = false

    /**
     * @return Whether the column is unique
     */
    boolean isUnique() {
        if (unique instanceof Boolean) {
            return (Boolean) unique
        }
        return unique != null && unique != false
    }
    /**
     * The length of the column
     */
    int length = -1
    /**
     * The precision of the column
     */
    int precision = -1
    /**
     * The scale of the column
     */
    int scale = -1
    /**
     * The default value
     */
    String defaultValue
    /**
     * A comment to apply to the column
     */
    String comment
    /**
     * A custom read string
     */
    String read
    /**
     * A custom write sstring
     */
    String write

    String toString() {
        "column[name:$name, index:$index, unique:$unique, length:$length, precision:$precision, scale:$scale]"
    }

    /**
     * Configures a new PropertyConfig instance
     *
     * @param config The configuration
     * @return The new instance
     */
    static ColumnConfig configureNew(@DelegatesTo(ColumnConfig) Closure config) {
        ColumnConfig property = new ColumnConfig()
        return configureExisting(property, config)
    }

    /**
     * Configures a new PropertyConfig instance
     *
     * @param config The configuration
     * @return The new instance
     */
    static ColumnConfig configureNew(Map config) {
        ColumnConfig property = new ColumnConfig()
        return configureExisting(property, config)
    }
    /**
     * Configures an existing PropertyConfig instance
     *
     * @param config The configuration
     * @return The new instance
     */
    static ColumnConfig configureExisting(ColumnConfig property, @DelegatesTo(ColumnConfig) Closure config) {
        config.setDelegate(property)
        config.setResolveStrategy(Closure.DELEGATE_ONLY)
        config.call()
        return property
    }

    /**
     * Configures an existing PropertyConfig instance
     *
     * @param config The configuration
     * @return The new instance
     */
    static ColumnConfig configureExisting(ColumnConfig column, Map config) {
        DataBinder dataBinder = new DataBinder(column)
        dataBinder.bind(new MutablePropertyValues(config))
        return column
    }
}
