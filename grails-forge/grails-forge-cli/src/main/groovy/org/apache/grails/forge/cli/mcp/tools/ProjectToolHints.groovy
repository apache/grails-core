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
package org.apache.grails.forge.cli.mcp.tools

import groovy.transform.CompileStatic

/**
 * Which hints of a tool of the application or of a plugin {@code grails mcp} passes on to its client.
 * The protocol has clients treat the annotations of a tool as untrusted unless they come from a server
 * they trust, and a client may approve, or run beside each other, the calls of tools that say they
 * change nothing. A plugin must not get that by saying so: the server passes on the hints of a tool
 * only for the origins it vouches for, the application itself and the plugins of Grails, whose group is
 * {@code org.apache.grails} or starts with {@code org.apache.grails.}. The tools of any other origin
 * get the defaults of the protocol, and their declaration is still in the {@code _meta} of the tool.
 *
 * @since 9.0
 */
@CompileStatic
final class ProjectToolHints {

    /** The origin of a tool of the application itself, as the catalog of the tools of a project has it. */
    public static final String APPLICATION = 'application'

    /** The group of the plugins of Grails. */
    public static final String GRAILS_GROUP = 'org.apache.grails'

    /** The four hints of a tool, in the order of the protocol. */
    public static final List<String> HINTS = ['readOnlyHint', 'destructiveHint', 'idempotentHint', 'openWorldHint'].asImmutable()

    /** The hints of a tool that says nothing of what it does: the defaults of the protocol, which are the most cautious. */
    public static final Map<String, Object> DEFAULTS = Collections.unmodifiableMap(new LinkedHashMap<String, Object>([
            readOnlyHint: (Object) Boolean.FALSE,
            destructiveHint: (Object) Boolean.TRUE,
            idempotentHint: (Object) Boolean.FALSE,
            openWorldHint: (Object) Boolean.TRUE
    ]))

    private ProjectToolHints() {
    }

    /**
     * @param origin where a tool comes from, as {@code help --json} says it: {@code application}, or the
     * coordinates of the jar of a plugin, {@code group:artifact:version}
     * @return whether the server passes on the hints of a tool of that origin: the application, and the
     * plugins whose group is {@code org.apache.grails} or starts with {@code org.apache.grails.}
     */
    static boolean vouchedFor(String origin) {
        if (origin == null) {
            return false
        }
        if (origin == APPLICATION) {
            return true
        }
        int colon = origin.indexOf(':')
        if (colon <= 0) {
            return false
        }
        String group = origin.substring(0, colon)
        return group == GRAILS_GROUP || group.startsWith(GRAILS_GROUP + '.')
    }

    /**
     * @param entry the entry of a tool in {@code help --json}
     * @return the annotations the server gives the tool: the four hints it declares, where its origin is
     * one the server vouches for and it declares them, a hint it does not declare as a boolean having the
     * default of the protocol; otherwise none, which a client reads as the defaults of the protocol
     */
    static Map<String, Object> annotations(Map<String, Object> entry) {
        Object declared = entry.get('hints')
        if (!(declared instanceof Map) || ((Map) declared).isEmpty() || !vouchedFor(entry.get('origin') instanceof String ? (String) entry.get('origin') : null)) {
            return Collections.<String, Object>emptyMap()
        }
        Map<String, Object> hints = new LinkedHashMap<>()
        for (String hint : HINTS) {
            Object value = ((Map) declared).get(hint)
            hints.put(hint, value instanceof Boolean ? value : DEFAULTS.get(hint))
        }
        return hints
    }
}
