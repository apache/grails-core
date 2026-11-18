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
package org.grails.openapi

import java.util.regex.Matcher
import java.util.regex.Pattern

import groovy.transform.CompileStatic

import io.swagger.v3.oas.models.media.Schema

import grails.gorm.validation.Constrained
import grails.gorm.validation.ConstrainedProperty
import grails.web.mapping.UrlMapping

/**
 * Converts the pattern of a Grails URL mapping to the OpenAPI paths it serves.
 *
 * <p>An OpenAPI path variable is always required, so a mapping with an optional variable, such as
 * {@code "/photos/$id?"}, serves two paths: one with the variable and one without. A mapping with
 * a wildcard that captures nothing, such as {@code "/**"}, matches paths that cannot be written as
 * an OpenAPI path and serves none.</p>
 */
@CompileStatic
class UrlMappingPaths {

    private static final Pattern TEMPLATE_VARIABLE = ~/\{([^}]+)\}/
    private static final String FORMAT_PARAMETER_NAME = 'format'
    private static final String OPTIONAL_EXTENSION_SUFFIX = UrlMapping.OPTIONAL_EXTENSION_WILDCARD + '?'

    /**
     * @param mapping the mapping to describe
     * @param substitutions the value a variable is fixed to, such as the controller a default
     * mapping is expanded for
     * @param omitted the variables the described action does not address; the segment each sits
     * in is left out
     * @return every path the mapping serves, the one with every variable first
     */
    static List<String> paths(UrlMapping mapping, Map<String, String> substitutions = [:],
                              Set<String> omitted = [] as Set) {
        String pattern = stripOptionalExtension(mapping.urlData?.urlPattern)
        if (pattern == null) {
            return Collections.<String> emptyList()
        }

        List<String> names = variableNames(mapping)
        StringBuilder result = new StringBuilder()
        // Where the path may end instead: before the segment of each optional variable.
        List<Integer> optionalEnds = []
        int index = 0
        int nameIndex = 0

        while (index < pattern.length()) {
            boolean greedy = pattern.startsWith(UrlMapping.CAPTURED_DOUBLE_WILDCARD, index)
            if (greedy || pattern.startsWith(UrlMapping.CAPTURED_WILDCARD, index)) {
                String name = nameIndex < names.size() ? names[nameIndex] : "param${nameIndex}".toString()
                index += (greedy ? UrlMapping.CAPTURED_DOUBLE_WILDCARD : UrlMapping.CAPTURED_WILDCARD).length()
                nameIndex++
                boolean optional = index < pattern.length() && pattern.charAt(index) == ('?' as char)
                if (optional) {
                    index++
                }

                if (omitted.contains(name)) {
                    // The action does not address this variable, so the segment it sits in goes too.
                    truncateToLastSegment(result)
                }
                else if (substitutions.containsKey(name)) {
                    result.append(substitutions[name])
                }
                else {
                    if (optional) {
                        optionalEnds << result.lastIndexOf(UrlMapping.SLASH)
                    }
                    result.append('{').append(name).append('}')
                }
            }
            else {
                result.append(pattern.charAt(index))
                index++
            }
        }

        String full = normalize(result.toString())
        if (full.contains('*')) {
            return Collections.<String> emptyList()
        }

        List<String> paths = [full]
        for (Integer end : optionalEnds.reverse()) {
            String shorter = normalize(result.substring(0, Math.max(end, 0)))
            if (!paths.contains(shorter)) {
                paths << shorter
            }
        }
        paths
    }

    /**
     * The variables a path declares, so every one of them is described and none that is absent is.
     */
    static List<String> templateVariables(String path) {
        List<String> names = []
        Matcher matcher = TEMPLATE_VARIABLE.matcher(path)
        while (matcher.find()) {
            names << matcher.group(1)
        }
        names
    }

    /**
     * Applies what the mapping constrains a variable to: the pattern it must match, where the
     * variable is described as a string, and the values it must be one of.
     */
    static void constrain(Schema<?> schema, UrlMapping mapping, String name) {
        ConstrainedProperty declared = (ConstrainedProperty) mapping?.constraints?.find { Constrained constrained ->
            constrained instanceof ConstrainedProperty && ((ConstrainedProperty) constrained).propertyName == name
        }
        if (declared == null || !CharSequence.isAssignableFrom(declared.propertyType)) {
            return
        }
        String type = schema.type ?: schema.types?.find()
        if (declared.matches && type == 'string') {
            // Grails requires the whole segment to match.
            schema.setPattern("^(?:${declared.matches})\$".toString())
        }
        if (declared.inList && !schema.enum) {
            declared.inList.each { Object value -> ((Schema<Object>) schema).addEnumItemObject(value) }
        }
    }

    /**
     * Whether a variable of the mapping may be left out of a request, as {@code $action?} may.
     */
    static boolean isOptional(UrlMapping mapping, String name) {
        Constrained declared = mapping.constraints?.find { Constrained constrained ->
            constrained instanceof ConstrainedProperty && ((ConstrainedProperty) constrained).propertyName == name
        }
        declared != null && declared.nullable
    }

    /**
     * The names of the variables a mapping binds, in the order its pattern declares them.
     */
    static List<String> variableNames(UrlMapping mapping) {
        Constrained[] constraints = mapping.constraints
        if (!constraints) {
            return Collections.<String> emptyList()
        }

        List<String> names = constraints.findResults { Constrained constrained ->
            constrained instanceof ConstrainedProperty ? ((ConstrainedProperty) constrained).propertyName : null
        } as List<String>

        // The format binding belongs to the optional extension that is stripped from the path.
        boolean hasExtension = mapping.urlData?.urlPattern?.endsWith(OPTIONAL_EXTENSION_SUFFIX)
        if (hasExtension && names && names.last() == FORMAT_PARAMETER_NAME) {
            names = names[0..<(names.size() - 1)]
        }
        names
    }

    /**
     * Removes the trailing Grails response format suffix. OpenAPI expresses the response format
     * through content types rather than a path segment.
     */
    private static String stripOptionalExtension(String pattern) {
        if (pattern == null) {
            return null
        }
        pattern.endsWith(OPTIONAL_EXTENSION_SUFFIX)
                ? pattern[0..<(pattern.length() - OPTIONAL_EXTENSION_SUFFIX.length())]
                : pattern
    }

    private static void truncateToLastSegment(StringBuilder result) {
        int slash = result.lastIndexOf(UrlMapping.SLASH)
        if (slash >= 0) {
            result.setLength(slash)
        }
    }

    private static String normalize(String path) {
        if (!path) {
            return UrlMapping.SLASH
        }
        path.startsWith(UrlMapping.SLASH) ? path : UrlMapping.SLASH + path
    }
}
