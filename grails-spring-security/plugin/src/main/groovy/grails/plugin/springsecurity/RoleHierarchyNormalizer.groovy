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
package grails.plugin.springsecurity

import groovy.transform.CompileStatic

/**
 * Rewrites a role hierarchy as Spring Security reads it. Spring Security splits a line only where the {@code >}
 * operator has whitespace on each side, and ignores a line such as {@code ROLE_ADMIN>ROLE_USER} without a word, so the
 * user did not get the role the hierarchy implies. Earlier versions of the plugin accepted that form, and this one still
 * does: each line is written with one space on each side of the operator, blank lines are dropped, and lines end in
 * a newline.
 */
@CompileStatic
final class RoleHierarchyNormalizer {

    private RoleHierarchyNormalizer() {
    }

    /**
     * @param hierarchy the hierarchy as configured, one assignment for each line, or {@code null}
     * @return the hierarchy with one space on each side of every operator, or the argument when it is {@code null} or blank
     */
    static String normalize(String hierarchy) {
        if (!hierarchy?.trim()) {
            return hierarchy
        }
        List<String> lines = []
        for (String line in hierarchy.readLines()) {
            String trimmed = line.trim()
            if (trimmed) {
                lines << (trimmed.contains('>') ? trimmed.split(/\s*>\s*/, -1).collect { String role -> role.trim() }.join(' > ') : trimmed)
            }
        }
        lines.join('\n')
    }
}
