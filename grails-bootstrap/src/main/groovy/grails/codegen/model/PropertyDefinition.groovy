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
package grails.codegen.model

import groovy.transform.CompileStatic

/**
 * Represents a property definition for domain class code generation.
 * Properties in Groovy have no access modifier and auto-generate getter/setter methods.
 *
 * @since 7.1
 */
@CompileStatic
class PropertyDefinition extends AbstractMemberDefinition {

    @Override
    protected String getMemberType() {
        'Property'
    }

    /**
     * Generates the property declaration line for the domain class.
     *
     * @return the property declaration (e.g., "String title")
     */
    @Override
    String toDeclaration() {
        "${type} ${name}"
    }

    /**
     * Parses a property specification string like "title:String" into a PropertyDefinition.
     *
     * @param propertySpec the property specification (e.g., "title:String" or just "title")
     * @return a new PropertyDefinition instance
     */
    static PropertyDefinition parse(String propertySpec) {
        if (!propertySpec) {
            throw new IllegalArgumentException('Property specification cannot be null or empty')
        }

        def parts = propertySpec.split(':', 2)
        def definition = new PropertyDefinition()
        definition.name = parts[0].trim()

        if (parts.length > 1 && parts[1]?.trim()) {
            definition.type = parts[1].trim()
        }

        definition
    }

    /**
     * Builder pattern for creating PropertyDefinition instances.
     */
    static class Builder {
        private PropertyDefinition definition = new PropertyDefinition()

        Builder name(String name) {
            definition.name = name
            this
        }

        Builder type(String type) {
            definition.type = type
            this
        }

        Builder nullable(Boolean nullable) {
            definition.nullable = nullable
            this
        }

        Builder blank(Boolean blank) {
            definition.blank = blank
            this
        }

        Builder maxSize(Integer maxSize) {
            definition.maxSize = maxSize
            this
        }

        Builder minSize(Integer minSize) {
            definition.minSize = minSize
            this
        }

        Builder constraintStyle(AbstractMemberDefinition.ConstraintStyle constraintStyle) {
            definition.constraintStyle = constraintStyle
            this
        }

        PropertyDefinition build() {
            definition.validate()
            definition
        }
    }

    static Builder builder() {
        new Builder()
    }
}
