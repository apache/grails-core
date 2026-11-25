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
package org.apache.grails.forge.core.state

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import java.util.regex.Pattern

/**
 * One mapping of a file of the state, read key by key. A value that is not what its key takes, and a
 * key that nothing read, are refused with the file and the place in it: the files are written by
 * Forge, so what Forge does not write was put there by something else.
 */
@CompileStatic
@PackageScope
final class StateSection {

    private final String file
    private final String at
    private final Map<String, Object> values
    private final Set<String> read = new HashSet<>()

    StateSection(String file, String at, Map<String, Object> values) {
        this.file = file
        this.at = at
        this.values = values
    }

    ProjectStateException problem(String key, String what) {
        return new ProjectStateException("${file}: ${at}${key} ${what}")
    }

    boolean has(String key) {
        return values.containsKey(key)
    }

    /** The value of a key as it was read, for a reader with a rule of its own for it. */
    Object value(String key) {
        read << key
        return values.get(key)
    }

    String text(String key) {
        return text(key, StateLimits.MAX_TEXT)
    }

    String text(String key, int limit) {
        read << key
        Object value = values.get(key)
        if (!(value instanceof String) || !StateYaml.isLine((String) value, limit)) {
            throw problem(key, value == null ? 'is missing' :
                    "must be one line of at most ${limit} characters, none of them a control character or one that is not shown")
        }
        return (String) value
    }

    String optionalText(String key) {
        return has(key) ? text(key) : null
    }

    String matching(String key, Pattern pattern, String rule) {
        return matching(key, pattern, rule, StateLimits.MAX_TEXT)
    }

    String matching(String key, Pattern pattern, String rule, int limit) {
        String value = text(key, limit)
        if (!pattern.matcher(value).matches()) {
            throw problem(key, "must be ${rule}, but is '${StateYaml.shown(value, StateYaml.MAX_ECHO)}'")
        }
        return value
    }

    String optionalMatching(String key, Pattern pattern, String rule) {
        return optionalMatching(key, pattern, rule, StateLimits.MAX_TEXT)
    }

    String optionalMatching(String key, Pattern pattern, String rule, int limit) {
        return has(key) ? matching(key, pattern, rule, limit) : null
    }

    String oneOf(String key, Collection<String> allowed) {
        String value = text(key)
        if (!allowed.contains(value)) {
            throw problem(key, "must be one of ${allowed.join(', ')}, but is '${StateYaml.shown(value, StateYaml.MAX_ECHO)}'")
        }
        return value
    }

    int number(String key, int least, int most) {
        String value = text(key)
        if (!(value ==~ /0|[1-9][0-9]{0,8}/) || Integer.parseInt(value) < least || Integer.parseInt(value) > most) {
            throw problem(key, "must be a number from ${least} to ${most}, but is '${StateYaml.shown(value, StateYaml.MAX_ECHO)}'")
        }
        return Integer.parseInt(value)
    }

    boolean flag(String key) {
        return oneOf(key, ['true', 'false']) == 'true'
    }

    StateSection section(String key) {
        read << key
        Object value = values.get(key)
        if (!(value instanceof Map)) {
            throw problem(key, value == null ? 'is missing' : 'must be a mapping')
        }
        return new StateSection(file, "${at}${key}.", (Map<String, Object>) value)
    }

    List<StateSection> sections(String key, int most) {
        read << key
        Object value = values.get(key)
        if (!(value instanceof List)) {
            throw problem(key, value == null ? 'is missing' : 'must be a list')
        }
        List<Object> entries = (List<Object>) value
        if (entries.size() > most) {
            throw problem(key, "has ${entries.size()} entries, and can have ${most}")
        }
        List<StateSection> sections = new ArrayList<>(entries.size())
        for (int index = 0; index < entries.size(); index++) {
            if (!(entries.get(index) instanceof Map)) {
                throw problem("${key}[${index}]", 'must be a mapping')
            }
            sections << new StateSection(file, "${at}${key}[${index}].", (Map<String, Object>) entries.get(index))
        }
        return sections
    }

    Map<String, String> texts(String key, int most) {
        StateSection section = section(key)
        if (section.values.size() > most) {
            throw problem(key, "has ${section.values.size()} entries, and can have ${most}")
        }
        Map<String, String> texts = new LinkedHashMap<>()
        for (Object name : section.values.keySet()) {
            if (!(name instanceof String) || !StateYaml.isLine((String) name, StateLimits.MAX_TEXT)) {
                throw problem(key, 'has a key that is not one line of text')
            }
            texts.put((String) name, section.text((String) name))
        }
        return texts
    }

    /**
     * @return the keys of the section, each one line of text
     */
    List<String> keys() {
        List<String> keys = []
        for (Object name : values.keySet()) {
            if (!(name instanceof String) || !StateYaml.isLine((String) name, StateLimits.MAX_TEXT)) {
                throw new ProjectStateException("${file}: ${at ?: 'the file'} has a key that is not one line of text")
            }
            keys << (String) name
        }
        return keys
    }

    /** Refuses a key that nothing read. */
    void end() {
        for (Object key : values.keySet()) {
            if (!read.contains(key)) {
                throw new ProjectStateException("${file}: ${at}${StateYaml.shown(String.valueOf(key), StateYaml.MAX_ECHO)} is not a key this version of Grails reads")
            }
        }
    }
}
