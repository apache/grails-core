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
 * Represents a field definition for domain class code generation.
 * Fields in Groovy have an explicit access modifier and do not auto-generate getter/setter methods.
 *
 * @since 7.1
 */
@CompileStatic
class FieldDefinition extends AbstractMemberDefinition {

    /**
     * Access modifier options for fields.
     */
    enum AccessModifier {
        PRIVATE('private'),
        PROTECTED('protected'),
        PUBLIC('public')

        final String keyword

        AccessModifier(String keyword) {
            this.keyword = keyword
        }

        @Override
        String toString() {
            keyword
        }
    }

    AccessModifier accessModifier

    @Override
    protected String getMemberType() {
        'Field'
    }

    @Override
    void validate() {
        super.validate()

        if (accessModifier == null) {
            throw new IllegalArgumentException('Access modifier is required for fields. Use --private, --protected, or --public')
        }
    }

    /**
     * Generates the field declaration line for the domain class.
     *
     * @return the field declaration (e.g., "private String title")
     */
    @Override
    String toDeclaration() {
        "${accessModifier.keyword} ${type} ${name}"
    }

    /**
     * Parses a field specification string like "title:String" into a FieldDefinition.
     * Note: The access modifier must be set separately.
     *
     * @param fieldSpec the field specification (e.g., "title:String" or just "title")
     * @return a new FieldDefinition instance
     */
    static FieldDefinition parse(String fieldSpec) {
        if (!fieldSpec) {
            throw new IllegalArgumentException('Field specification cannot be null or empty')
        }

        def parts = fieldSpec.split(':', 2)
        def definition = new FieldDefinition()
        definition.name = parts[0].trim()

        if (parts.length > 1 && parts[1]?.trim()) {
            definition.type = parts[1].trim()
        }

        definition
    }

    /**
     * Builder pattern for creating FieldDefinition instances.
     */
    static class Builder {
        private FieldDefinition definition = new FieldDefinition()

        Builder name(String name) {
            definition.name = name
            this
        }

        Builder type(String type) {
            definition.type = type
            this
        }

        Builder accessModifier(AccessModifier accessModifier) {
            definition.accessModifier = accessModifier
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

        FieldDefinition build() {
            definition.validate()
            definition
        }
    }

    /**
     * Factory for the {@link FieldDefinition.Builder}. The return type is intentionally fully qualified:
     * {@code PropertyDefinition} declares an inner Builder of the same simple name, and groovydoc resolves
     * unqualified {@code Builder} references inconsistently between builds (depends on filesystem iteration
     * order). Keep this qualified to ensure groovydoc renders {@code FieldDefinition.Builder} reproducibly.
     *
     * TODO(GROOVY-11954): Revisit and simplify the return type to {@code Builder} once Grails depends on
     * Groovy 4.0.32+ (also fixed in 5.0.6 and 6.0.0-alpha-1). The upstream fix corrects the shared
     * short-name resolution cache in {@code SimpleGroovyClassDoc} that causes this non-reproducibility,
     * so the workaround here will no longer be needed. As of writing, Grails pins {@code groovy.version=4.0.31}
     * and 4.0.32 has not yet been released to Maven Central. See:
     * https://issues.apache.org/jira/browse/GROOVY-11954 and https://github.com/apache/groovy/pull/2484.
     */
    static FieldDefinition.Builder builder() {
        new FieldDefinition.Builder()
    }
}
