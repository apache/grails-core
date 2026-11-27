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
 * The frozen oracle of the differential specs: what the classic domain binder produced for the scanned test domains, recorded once
 * as text under {@code src/test/resources/classic-oracle/} so that the specs compare the native binding against recorded data
 * instead of against a live classic binder.
 *
 * <p>There are three modes, chosen with Gradle properties (wired to system properties in {@code gradle/hibernate7-test-config.gradle}):</p>
 * <ul>
 *   <li><b>FROZEN</b> (the default): the classic binder is not booted; the recorded files are the oracle.</li>
 *   <li><b>VERIFY</b> ({@code -Pgrails.test.verifyClassicOracle=true}): the classic binder is booted live, what it produces is
 *   serialised the way the files are and must EQUAL the recorded files (the freeze is faithful and current); the comparison then runs
 *   as in FROZEN mode.</li>
 *   <li><b>REFREEZE</b> ({@code -Pgrails.test.refreezeClassicOracle=true}): the classic binder is booted live and the files are rewritten.</li>
 * </ul>
 *
 * <p>Once the classic binder is deleted VERIFY and REFREEZE go with it; a new fixture then gets its recorded entries by hand.</p>
 *
 * <p>The files are plain text, one record per line, sorted and without anything that varies from run to run. A line is
 * {@code kind TAB key TAB json}; a line {@code @group TAB key TAB json} opens the section of one group of domain classes, whose
 * header lists the member classes (so a changed fixture set is detected) and, for a group the classic binder cannot boot, the
 * first line of the reason.</p>
 */
class ClassicOracle {

    static final String VERIFY_PROPERTY = 'grails.test.verifyClassicOracle'
    static final String REFREEZE_PROPERTY = 'grails.test.refreezeClassicOracle'
    static final String RESOURCE_DIRECTORY = 'classic-oracle'
    static final String REFREEZE_HINT = 'run the spec with -Pgrails.test.refreezeClassicOracle=true (it boots the classic binder and rewrites ' +
            'src/test/resources/classic-oracle/) and commit the changed files'

    enum Mode { FROZEN, VERIFY, REFREEZE }

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
    private final Map<String, Section> recorded = new LinkedHashMap<String, Section>()
    private final Map<String, Section> fresh = new LinkedHashMap<String, Section>()
    private final Set<String> visited = new LinkedHashSet<String>()
    final List<String> drift = []

    ClassicOracle(String name) {
        this(name, currentMode())
    }

    ClassicOracle(String name, Mode mode) {
        this.name = name
        this.mode = mode
        if (mode != Mode.REFREEZE) {
            recorded.putAll(read(name))
        }
    }

    static Mode currentMode() {
        if (Boolean.getBoolean(REFREEZE_PROPERTY)) {
            return Mode.REFREEZE
        }
        return Boolean.getBoolean(VERIFY_PROPERTY) ? Mode.VERIFY : Mode.FROZEN
    }

    /** The recorded section of the group whose first member is the key, or null: for the specs of the oracle itself. */
    Section recordedSection(String key) {
        return recorded[key]
    }

    Set<String> recordedKeys() {
        return recorded.keySet()
    }

    /** True when the classic binder has to be booted (VERIFY and REFREEZE). */
    boolean isLive() {
        return mode != Mode.FROZEN
    }

    /**
     * The recorded section of a group. In FROZEN mode the closure is not called; in VERIFY mode it is called and its section must equal
     * the recorded one; in REFREEZE mode it is called and its section becomes the record.
     *
     * @param key the first member of the group (groups never share a class)
     * @param members the names of the classes of the group
     * @param classic boots the classic binder and builds the section
     */
    Section section(String key, List<String> members, Closure<Section> classic) {
        visited << key
        if (mode == Mode.REFREEZE) {
            Section section = classic.call()
            fresh[key] = section
            return section
        }
        Section stored = recorded[key]
        if (stored == null || stored.header.members != members) {
            throw new IllegalStateException(
                    "The classic oracle '${name}' has no recorded data for the group of ${key} with the classes ${members} " +
                            "(a new or changed fixture): ${REFREEZE_HINT}".toString())
        }
        if (mode == Mode.VERIFY) {
            Section live = classic.call()
            fresh[key] = live
            if (live.render() != stored.render()) {
                drift << "${key}: ${firstDifference(stored.render(), live.render())}".toString()
            }
        }
        return stored
    }

    /** Ends a run: rewrites the files (REFREEZE), and reports recorded groups no fixture backs any more. */
    void finish() {
        if (mode == Mode.REFREEZE) {
            File file = new File("src/test/resources/${RESOURCE_DIRECTORY}/${name}.txt")
            file.parentFile.mkdirs()
            file.setText(render(fresh), 'UTF-8')
            return
        }
        recorded.keySet().findAll { String key -> !visited.contains(key) }.each { String key ->
            drift << "${key}: recorded in '${name}' but no scanned fixture makes up this group any more".toString()
        }
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
            throw new IllegalStateException("The classic oracle file ${path} is missing: ${REFREEZE_HINT}".toString())
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

    private static String firstDifference(String recorded, String live) {
        List<String> a = recorded.readLines()
        List<String> b = live.readLines()
        for (int i = 0; i < Math.max(a.size(), b.size()); i++) {
            String left = i < a.size() ? a[i] : '<missing>'
            String right = i < b.size() ? b[i] : '<missing>'
            if (left != right) {
                return "line ${i + 1} differs: recorded=${left.take(300)} live=${right.take(300)}".toString()
            }
        }
        return 'no difference'
    }

    /**
     * The deterministic serialisation: map keys sorted, lists in order, no spaces; a map entry whose value is null is left out unless
     * {@code keepNulls} (a recorded fact that is absent reads as null).
     */
    static String canonical(Object value, boolean keepNulls = false) {
        StringBuilder out = new StringBuilder()
        write(out, value, keepNulls)
        // the domain binder names an index after the closure mapped as the index of a collection (...$_closure2@3ff5aef4), which holds
        // the identity of the closure instance and differs on every boot
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
