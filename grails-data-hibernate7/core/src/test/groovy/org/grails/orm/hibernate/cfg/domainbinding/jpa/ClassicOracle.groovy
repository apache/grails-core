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
package org.grails.orm.hibernate.cfg.domainbinding.jpa

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

/**
 * The frozen oracle of the differential specs: what the classic domain binder of Grails 8 produced for the scanned test domains,
 * recorded once, before it was deleted, as text under {@code src/test/resources/classic-oracle/}. The specs compare native binding
 * against these recorded facts.
 *
 * <p>There are two modes, chosen with a Gradle property (wired to a system property in {@code gradle/hibernate7-test-config.gradle}):</p>
 * <ul>
 *   <li><b>FROZEN</b> (the default): the recorded files are the oracle. A scanned group with no record, or whose classes changed,
 *   fails with a message that says how to record it; a recorded group no fixture makes up any more is reported as drift.</li>
 *   <li><b>RECORD</b> ({@code -Pgrails.test.recordClassicOracle=true}): the way a contributor adds a fixture. A scanned group that
 *   has no record (or whose classes changed) is booted natively, by the closure the spec passes, and its facts are written to the
 *   file at its place in the scan order; the records of every other group are written back as they are, and the records of groups
 *   no fixture makes up any more are dropped. The diff is what the contributor reviews: for a new fixture it is only additions, and
 *   they state what native binding does, since no classic binder is left to ask. Such a section carries {@code source: native} in
 *   its header: the DDL differential compares its schema (a later change of native binding shows up), the generator differential
 *   does not compare its facets, which only a classic boot could give, and reports it.</li>
 * </ul>
 *
 * <p>The files are plain text, one record per line, sorted and without anything that varies from run to run. A line is
 * {@code kind TAB key TAB json}; a line {@code @group TAB key TAB json} opens the section of one group of domain classes, whose
 * header lists the member classes (so a changed fixture set is detected) and, for a group that cannot boot, the first line of
 * the reason.</p>
 */
class ClassicOracle {

    static final String RECORD_PROPERTY = 'grails.test.recordClassicOracle'
    /** The header value that marks a section recorded from a native boot. */
    static final String NATIVE_SOURCE = 'native'
    static final String RESOURCE_DIRECTORY = 'classic-oracle'
    static final File DEFAULT_DIRECTORY = new File("src/test/resources/${RESOURCE_DIRECTORY}")
    static final String RECORD_HINT = 'run the differential specs with -Pgrails.test.recordClassicOracle=true: a scanned group that has ' +
            'no record is booted natively and its facts are written to src/test/resources/classic-oracle/, the records of groups no ' +
            'fixture makes up any more are dropped, and every other record is written back as it is; review the diff and commit it'

    enum Mode { FROZEN, RECORD }

    /** One group of domain classes: its header and its records. */
    static class Section {

        String key
        Map header = [:]
        List<List<String>> records = []

        Section(String key, Map header) {
            this.key = key
            this.header = header
        }

        void add(String kind, String recordKey, Object json, boolean keepNulls = false) {
            records << [kind, recordKey, canonical(json, keepNulls)]
        }

        List<Object> parsed(String kind) {
            return records.findAll { List<String> record -> record[0] == kind }.collect { List<String> record -> parse(record[2]) }
        }

        Map<String, Object> parsedByKey(String kind) {
            Map<String, Object> result = new LinkedHashMap<String, Object>()
            records.findAll { List<String> record -> record[0] == kind }.each { List<String> record -> result[record[1]] = parse(record[2]) }
            return result
        }

        String render() {
            StringBuilder text = new StringBuilder()
            text << "@group\t${key}\t${canonical(header, true)}\n"
            records.each { List<String> record -> text << "${record[0]}\t${record[1]}\t${record[2]}\n" }
            return text.toString()
        }
    }

    final String name
    final Mode mode
    private final File directory
    private final Map<String, Section> recorded = new LinkedHashMap<String, Section>()
    private final Map<String, Section> fresh = new LinkedHashMap<String, Section>()
    private final List<String> visited = []
    /** FROZEN: the recorded groups no scanned fixture makes up any more. */
    final List<String> drift = []
    /** RECORD: the groups recorded by this run. */
    final List<String> recordedNow = []
    /** RECORD: the recorded groups dropped from the file because no scanned fixture makes them up any more. */
    final List<String> dropped = []

    ClassicOracle(String name) {
        this(name, currentMode())
    }

    ClassicOracle(String name, Mode mode, File directory = DEFAULT_DIRECTORY) {
        this.name = name
        this.mode = mode
        this.directory = directory
        recorded.putAll(read(name))
    }

    static Mode currentMode() {
        return Boolean.getBoolean(RECORD_PROPERTY) ? Mode.RECORD : Mode.FROZEN
    }

    /** The recorded section of the group whose first member is the key, or null: for the specs of the oracle itself. */
    Section recordedSection(String key) {
        return recorded[key]
    }

    Set<String> recordedKeys() {
        return recorded.keySet()
    }

    /**
     * The section of a group: the recorded one when the group is recorded with these classes. Otherwise the group is new or
     * changed: FROZEN fails and says how to record it, RECORD calls the closure and keeps its section for the file.
     *
     * @param key the first member of the group (groups never share a class)
     * @param members the names of the classes of the group
     * @param record boots the group natively and builds its section
     */
    Section section(String key, List<String> members, Closure<Section> record) {
        visited << key
        Section stored = recorded[key]
        if (stored != null && stored.header.members == members) {
            return stored
        }
        if (mode == Mode.FROZEN) {
            throw new IllegalStateException(
                    "The classic oracle '${name}' has no recorded data for the group of ${key} with the classes ${members} " +
                            "(a new or changed fixture): ${RECORD_HINT}".toString())
        }
        Section section = record.call()
        fresh[key] = section
        recordedNow << key
        return section
    }

    /**
     * Ends a run. FROZEN reports the recorded groups no fixture backs any more. RECORD writes the file when a group was recorded
     * or a recorded group is gone: the recorded sections in their order, a recorded group inserted after the scanned group that
     * precedes it, the groups no fixture makes up any more left out.
     */
    void finish() {
        List<String> stale = recorded.keySet().findAll { String key -> !visited.contains(key) }.toList()
        if (mode == Mode.FROZEN) {
            stale.each { String key ->
                drift << "${key}: recorded in '${name}' but no scanned fixture makes up this group any more: ${RECORD_HINT}".toString()
            }
            return
        }
        dropped.addAll(stale)
        if (fresh.isEmpty() && stale.isEmpty()) {
            return
        }
        List<String> order = recorded.keySet().findAll { String key -> visited.contains(key) }.toList()
        for (String key : recordedNow) {
            int at = visited.indexOf(key)
            String before = at == 0 ? null : (at - 1..0).collect { int i -> visited[i] }.find { String candidate -> order.contains(candidate) }
            order.add(before == null ? 0 : order.indexOf(before) + 1, key)
        }
        Map<String, Section> sections = new LinkedHashMap<String, Section>()
        order.each { String key -> sections[key] = fresh[key] ?: recorded[key] }
        File file = new File(directory, "${name}.txt")
        file.parentFile.mkdirs()
        file.setText(render(sections), 'UTF-8')
    }

    static String render(Map<String, Section> sections) {
        StringBuilder text = new StringBuilder()
        sections.values().each { Section section -> text << section.render() }
        return text.toString()
    }

    static Map<String, Section> read(String name) {
        String path = "/${RESOURCE_DIRECTORY}/${name}.txt"
        InputStream stream = ClassicOracle.getResourceAsStream(path)
        if (stream == null) {
            throw new IllegalStateException("The classic oracle file ${path} is missing: ${RECORD_HINT}".toString())
        }
        return stream.withCloseable { InputStream input -> parseText(input.getText('UTF-8')) }
    }

    static Map<String, Section> parseText(String text) {
        Map<String, Section> sections = new LinkedHashMap<String, Section>()
        Section current = null
        for (String line : text.readLines()) {
            if (line.isEmpty()) {
                continue
            }
            List<String> parts = line.split('\t', 3).toList()
            if (parts[0] == '@group') {
                current = new Section(parts[1], (Map) parse(parts[2]))
                sections[current.key] = current
            } else {
                current.records << [parts[0], parts[1], parts[2]]
            }
        }
        return sections
    }

    /**
     * The deterministic serialisation: map keys sorted, lists in order, no spaces; a map entry whose value is null is left out unless
     * {@code keepNulls} (a recorded fact that is absent reads as null).
     */
    static String canonical(Object value, boolean keepNulls = false) {
        StringBuilder out = new StringBuilder()
        write(out, value, keepNulls)
        // an index named after the closure mapped as the index of a collection (...$_closure2@3ff5aef4) holds the identity of the
        // closure instance, which differs on every boot
        return out.toString().replaceAll(/(_closure\d+)@[0-9a-f]+/, '$1@0')
    }

    private static void write(StringBuilder out, Object value, boolean keepNulls) {
        if (value == null) {
            out << 'null'
        } else if (value instanceof Map) {
            Map<String, Object> sorted = new TreeMap<String, Object>()
            ((Map<?, ?>) value).each { Object k, Object v -> sorted[k.toString()] = v }
            out << '{'
            boolean first = true
            for (Map.Entry<String, Object> entry : sorted.entrySet()) {
                if (entry.value == null && !keepNulls) {
                    continue
                }
                if (!first) {
                    out << ','
                }
                first = false
                out << JsonOutput.toJson(entry.key) << ':'
                write(out, entry.value, keepNulls)
            }
            out << '}'
        } else if (value instanceof Collection) {
            out << '['
            boolean first = true
            for (Object entry : (Collection<?>) value) {
                if (!first) {
                    out << ','
                }
                first = false
                write(out, entry, keepNulls)
            }
            out << ']'
        } else if (value instanceof Boolean || value instanceof Number) {
            out << value.toString()
        } else {
            out << JsonOutput.toJson(value.toString())
        }
    }

    static Object parse(String json) {
        return new JsonSlurper().parseText(json)
    }

    /** A reason a group cannot boot with the identity hash codes of objects left out, which differ from boot to boot. */
    static String stableReason(String reason) {
        return reason.replaceAll(/@[0-9a-f]{5,}/, '@#')
    }

    /** The value as the files hold it: serialised and parsed again, so that live and recorded data have the same types. */
    static Object normalized(Object value, boolean keepNulls = false) {
        return parse(canonical(value, keepNulls))
    }
}
