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

import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

/**
 * The frozen oracle itself ({@link ClassicOracle}): that its serialisation is deterministic, that the recorded files are the canonical
 * rendering of what they hold and cover every scanned fixture, that a fixture without recorded data is refused with a message that says how
 * to record it, and that the VERIFY mode notices a recorded group the classic binder no longer produces. That a changed recorded fact makes
 * the comparison fail is proven in {@code GrailsDomainGeneratorDifferentialSpec} and {@code GeneratedDomainClassesDdlDifferentialSpec},
 * which own the comparisons.
 */
class ClassicOracleSpec extends Specification {

    static final List<String> ORACLES = ['generator-differential', 'ddl-differential']

    @Shared
    List<List<Class<?>>> groups = ScannedDomainClasses.groupByAssociation(ScannedDomainClasses.findEntities())

    void "the serialisation is deterministic and independent of the order a map was built in"() {
        given:
        Map<String, Object> first = new LinkedHashMap<String, Object>()
        first.putAll([b: [z: 1, a: [true, null, 'text']], a: null, c: 'x'])
        Map<String, Object> second = new LinkedHashMap<String, Object>()
        second.putAll([c: 'x', a: null, b: [a: [true, null, 'text'], z: 1]])

        expect:
        ClassicOracle.canonical(first) == ClassicOracle.canonical(first)
        ClassicOracle.canonical(first) == ClassicOracle.canonical(second)
        ClassicOracle.canonical(first) == '{"b":{"a":[true,null,"text"],"z":1},"c":"x"}'
        ClassicOracle.canonical(first, true) == '{"a":null,"b":{"a":[true,null,"text"],"z":1},"c":"x"}'
    }

    void "a serialised value is read back as it was and serialises to the same text"() {
        given:
        Map<String, Object> value = [name: 'tab\tand\nnewline "quoted" é', count: 3, flag: false, list: [[x: 1], [x: 2]]]

        when:
        Object parsed = ClassicOracle.parse(ClassicOracle.canonical(value))

        then:
        parsed == value
        ClassicOracle.canonical(parsed) == ClassicOracle.canonical(value)
        !ClassicOracle.canonical(value).contains('\t')
        !ClassicOracle.canonical(value).contains('\n')
    }

    void "what varies from boot to boot is left out"() {
        expect:
        ClassicOracle.canonical([name: 'g.A$__clinit__closure1$_closure2@3ff5aef4']) == '{"name":"g.A$__clinit__closure1$_closure2@0"}'
        ClassicOracle.stableReason('Could not instantiate org.Foo@1b2c3d4e5 (see log)') == 'Could not instantiate org.Foo@# (see log)'
        ClassicOracle.stableReason('plain reason') == 'plain reason'
    }

    void "a section is rendered and parsed back to the same lines"() {
        given:
        ClassicOracle.Section section = new ClassicOracle.Section('x.First', [members: ['x.First', 'x.Second']])
        section.add('table', 'first_table', [columns: [id: [nullable: false, default: null]]], true)
        section.add('script', '', 'CREATE TABLE "T"(\n  "ID" BIGINT\t);', true)

        when:
        Map<String, ClassicOracle.Section> parsed = ClassicOracle.parseText(section.render())

        then:
        parsed.keySet() == ['x.First'].toSet()
        parsed['x.First'].header == [members: ['x.First', 'x.Second']]
        parsed['x.First'].parsedByKey('table') == ['first_table': [columns: [id: [nullable: false, default: null]]]]
        parsed['x.First'].parsed('script') == ['CREATE TABLE "T"(\n  "ID" BIGINT\t);']
        ClassicOracle.render(parsed) == section.render()
    }

    @Unroll
    void "the recorded file #oracle is the canonical rendering of what it holds"() {
        given:
        String text = ClassicOracle.getResourceAsStream("/${ClassicOracle.RESOURCE_DIRECTORY}/${oracle}.txt").getText('UTF-8')

        expect:
        ClassicOracle.render(ClassicOracle.read(oracle)) == text

        where:
        oracle << ORACLES
    }

    @Unroll
    void "the recorded file #oracle holds every scanned group, and only those"() {
        given:
        ClassicOracle frozen = new ClassicOracle(oracle, ClassicOracle.Mode.FROZEN)
        Map<String, List<String>> scanned = groups.collectEntries { List<Class<?>> group -> [(group.first().name): group*.name] }

        expect: "the groups, with the classes they are made of, are the ones scanned now"
        frozen.recordedKeys() == scanned.keySet()
        scanned.every { String key, List<String> members -> frozen.recordedSection(key).header.members == members }

        and: "every scanned domain class is in one recorded group"
        scanned.values().flatten().toSet() == ScannedDomainClasses.findEntities()*.name.toSet()

        and: "a group the classic binder cannot boot has its reason and nothing else, and a group it booted has content, but for the few whose entities live in another data source"
        scanned.keySet().every { String key ->
            ClassicOracle.Section section = frozen.recordedSection(key)
            section.header.unbootable == null || (!section.header.unbootable.toString().isEmpty() && section.records.isEmpty())
        }
        scanned.keySet().count { String key ->
            ClassicOracle.Section section = frozen.recordedSection(key)
            section.header.unbootable == null && section.records.isEmpty()
        } <= 20
        scanned.keySet().count { String key -> !frozen.recordedSection(key).records.isEmpty() } > 700

        where:
        oracle << ORACLES
    }

    void "the groups the classic binder booted have their entities in the generator oracle and their tables and statements in the DDL oracle"() {
        given:
        ClassicOracle generator = new ClassicOracle('generator-differential', ClassicOracle.Mode.FROZEN)
        ClassicOracle ddl = new ClassicOracle('ddl-differential', ClassicOracle.Mode.FROZEN)
        Set<String> withEntities = generator.recordedKeys().findAll { String key -> !generator.recordedSection(key).parsedByKey('entity').isEmpty() }
        Set<String> withTables = ddl.recordedKeys().findAll { String key -> !ddl.recordedSection(key).parsedByKey('table').isEmpty() }

        expect: "the groups the DDL oracle holds tables of are among those the generator oracle holds entities of (the DDL snapshot cannot describe a few more)"
        withEntities.size() > 700
        withEntities.containsAll(withTables)
        withTables.count { String key -> !ddl.recordedSection(key).parsed('script').isEmpty() } > 700
    }

    @Unroll
    void "a fixture with no recorded data fails with the way to record it (#oracle)"() {
        given:
        ClassicOracle frozen = new ClassicOracle(oracle, ClassicOracle.Mode.FROZEN)
        boolean booted = false

        when:
        frozen.section('org.example.NewFixture', ['org.example.NewFixture']) {
            booted = true
            return new ClassicOracle.Section('org.example.NewFixture', [members: ['org.example.NewFixture']])
        }

        then:
        IllegalStateException e = thrown()
        e.message.contains('org.example.NewFixture')
        e.message.contains('-Pgrails.test.refreezeClassicOracle=true')
        e.message.contains('src/test/resources/classic-oracle/')
        !booted

        where:
        oracle << ORACLES
    }

    @Unroll
    void "a recorded group whose classes changed fails with the way to record it (#oracle)"() {
        given:
        ClassicOracle frozen = new ClassicOracle(oracle, ClassicOracle.Mode.FROZEN)
        String key = frozen.recordedKeys().first()
        List<String> members = (List<String>) frozen.recordedSection(key).header.members

        when:
        frozen.section(key, members + ['org.example.AddedToTheGroup']) { null }

        then:
        IllegalStateException e = thrown()
        e.message.contains('-Pgrails.test.refreezeClassicOracle=true')

        where:
        oracle << ORACLES
    }

    void "the frozen mode does not call the classic binder for a recorded group and returns the recorded section"() {
        given:
        ClassicOracle frozen = new ClassicOracle('ddl-differential', ClassicOracle.Mode.FROZEN)
        String key = frozen.recordedKeys().first()
        List<String> members = (List<String>) frozen.recordedSection(key).header.members
        boolean booted = false

        when:
        ClassicOracle.Section section = frozen.section(key, members) { booted = true; null }

        then:
        !booted
        !frozen.isLive()
        section.is(frozen.recordedSection(key))
        frozen.drift.isEmpty()
    }

    void "the verify mode notices a group the classic binder no longer produces as it was recorded"() {
        given:
        ClassicOracle verify = new ClassicOracle('ddl-differential', ClassicOracle.Mode.VERIFY)
        ClassicOracle.Section recorded = verify.recordedSection(verify.recordedKeys().find {
            verify.recordedSection(it).header.unbootable == null
        })
        List<String> members = (List<String>) recorded.header.members
        ClassicOracle.Section same = new ClassicOracle.Section(recorded.key, recorded.header)
        same.records.addAll(recorded.records)
        ClassicOracle.Section other = new ClassicOracle.Section(recorded.key, recorded.header)
        other.records.addAll(recorded.records)
        other.records[0] = [other.records[0][0], other.records[0][1], '{"changed":true}']

        when:
        verify.section(recorded.key, members) { same }

        then: "the same section is the recorded one"
        verify.isLive()
        verify.drift.isEmpty()

        when:
        verify.section(recorded.key, members) { other }

        then: "a section that differs is reported with the first line that does"
        verify.drift.size() == 1
        verify.drift[0].contains(recorded.key)
        verify.drift[0].contains('differs')
    }

    void "the verify mode reports recorded groups that were not visited"() {
        given:
        ClassicOracle verify = new ClassicOracle('generator-differential', ClassicOracle.Mode.VERIFY)

        when:
        verify.finish()

        then:
        verify.drift.size() == verify.recordedKeys().size()
        verify.drift.every { String line -> line.contains('no scanned fixture') }
    }
}
