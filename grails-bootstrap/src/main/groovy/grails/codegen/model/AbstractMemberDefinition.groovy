/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package grails.codegen.model

import groovy.transform.CompileStatic

/**
 * Abstract base class for domain class member definitions (properties and fields).
 * Contains shared logic for validation, constraints, and annotation generation.
 *
 * @since 7.1
 */
@CompileStatic
abstract class AbstractMemberDefinition {

    /**
     * Constraint style options for member validation.
     */
    enum ConstraintStyle {
        /** Use Grails static constraints block */
        GRAILS,
        /** Use Jakarta Validation annotations */
        JAKARTA,
        /** Use both Grails constraints and Jakarta annotations */
        BOTH
    }

    /**
     * Built-in types that don't require imports.
     */
    static final Set<String> BUILTIN_TYPES = [
        'String', 'Integer', 'Long', 'Boolean', 'Date', 'BigDecimal',
        'Double', 'Float', 'Short', 'Byte', 'Character'
    ] as Set

    String name
    String type = 'String'
    Boolean nullable
    Boolean blank
    Integer maxSize
    Integer minSize
    ConstraintStyle constraintStyle = ConstraintStyle.GRAILS

    /**
     * Validates the member definition.
     *
     * @throws IllegalArgumentException if validation fails
     */
    void validate() {
        if (!name || !name.trim()) {
            throw new IllegalArgumentException(getMemberType() + ' name is required')
        }

        if (!name.matches(/^[a-z][a-zA-Z0-9]*$/)) {
            throw new IllegalArgumentException(getMemberType() + " name '${name}' must start with a lowercase letter and contain only alphanumeric characters")
        }

        if (!isValidTypeName(type)) {
            throw new IllegalArgumentException("Invalid ${getMemberType().toLowerCase()} type '${type}'. Type must start with an uppercase letter (e.g., String, Author, Status)")
        }

        if (blank != null && type != 'String') {
            throw new IllegalArgumentException("The 'blank' constraint is only applicable to String ${getMemberType().toLowerCase()}s")
        }

        if (maxSize != null && type != 'String') {
            throw new IllegalArgumentException("The 'maxSize' constraint is only applicable to String ${getMemberType().toLowerCase()}s")
        }

        if (minSize != null && type != 'String') {
            throw new IllegalArgumentException("The 'minSize' constraint is only applicable to String ${getMemberType().toLowerCase()}s")
        }
    }

    /**
     * Returns the member type name for error messages (e.g., 'Property' or 'Field').
     */
    protected abstract String getMemberType()

    /**
     * Generates the declaration line for the domain class.
     *
     * @return the declaration (e.g., 'String title' or 'private String title')
     */
    abstract String toDeclaration()

    /**
     * Generates the constraint line for the constraints block.
     * Returns null if no constraints are needed.
     *
     * @return the constraint line (e.g., 'title blank: false, maxSize: 255') or null
     */
    String toConstraintLine() {
        List<String> constraints = []

        if (nullable != null) {
            constraints.add('nullable: ' + nullable)
        }

        if (blank != null) {
            constraints.add('blank: ' + blank)
        }

        if (maxSize != null) {
            constraints.add('maxSize: ' + maxSize)
        }

        if (minSize != null) {
            constraints.add('minSize: ' + minSize)
        }

        if (constraints.empty) {
            return null
        }

        "${name} ${constraints.join(', ')}"
    }

    /**
     * Generates Jakarta Validation annotations for the member.
     * Returns an empty list if no annotations are needed.
     *
     * @return list of annotation strings (e.g., ['@NotNull', '@Size(max = 255)'])
     */
    List<String> toAnnotations() {
        List<String> annotations = []

        if (nullable != null && !nullable) {
            annotations.add('@NotNull')
        }

        if (blank != null && !blank && type == 'String') {
            annotations.add('@NotBlank')
        }

        if (maxSize != null || minSize != null) {
            List<String> sizeParams = []
            if (minSize != null) {
                sizeParams.add('min = ' + minSize)
            }
            if (maxSize != null) {
                sizeParams.add('max = ' + maxSize)
            }
            annotations.add('@Size(' + sizeParams.join(', ') + ')')
        }

        annotations
    }

    /**
     * Gets the required import statements for Jakarta Validation annotations.
     *
     * @return set of fully qualified import class names
     */
    Set<String> getRequiredImports() {

        Set<String> imports = [] as Set

        if (nullable != null && !nullable) {
            imports.add('jakarta.validation.constraints.NotNull')
        }

        if (blank != null && !blank && type == 'String') {
            imports.add('jakarta.validation.constraints.NotBlank')
        }

        if (maxSize != null || minSize != null) {
            imports.add('jakarta.validation.constraints.Size')
        }

        imports
    }

    /**
     * Determines if this member should use Grails constraints.
     */
    boolean usesGrailsConstraints() {
        constraintStyle == ConstraintStyle.GRAILS || constraintStyle == ConstraintStyle.BOTH
    }

    /**
     * Determines if this member should use Jakarta annotations.
     */
    boolean usesJakartaAnnotations() {
        constraintStyle == ConstraintStyle.JAKARTA || constraintStyle == ConstraintStyle.BOTH
    }

    /**
     * Checks if the type is a built-in type that doesn't require imports.
     */
    boolean isBuiltinType() {
        BUILTIN_TYPES.contains(type)
    }

    /**
     * Validates that a type name looks like a valid class/type name.
     * Allows simple names (String), qualified names (java.util.Date), and generics ({@code List<Book>}).
     */
    protected static boolean isValidTypeName(String typeName) {
        if (!typeName || typeName.isAllWhitespace()) {
            return false
        }
        // Extract simple name (last segment after dots, before any generics)
        String simpleName = typeName.contains('.') ?
            typeName.substring(typeName.lastIndexOf('.') + 1).replaceAll(/<.*/, '') :
            typeName.replaceAll(/<.*/, '')
        // Simple name must start with uppercase letter
        simpleName.matches(/^[A-Z][a-zA-Z0-9]*$/)
    }
}
