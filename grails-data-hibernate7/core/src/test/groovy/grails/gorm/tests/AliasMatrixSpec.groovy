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
package grails.gorm.tests

import spock.lang.Issue

import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity

/**
 * Runs every combination of the ways a criteria query names an association (an explicit alias, an association
 * block, a dotted path, a join type) against a fixture that tells the combinations apart, and compares the rows with
 * the ones Hibernate 5 returns, which are the reference. The same file runs on Hibernate 5 and on Hibernate 7, so a
 * combination that returns other rows on one of them is a difference, whether or not anyone thought of it.
 *
 * <p>The expected rows are in {@code alias-matrix.expected.txt}. Set {@code ALIAS_MATRIX_RECORD} to a file path to
 * write the rows the running module returns, which is how the file is made from Hibernate 5. A combination for which
 * Hibernate 5 throws has no reference rows, so another result is not a difference. A combination in
 * {@code alias-matrix.accepted.txt} is a rule for differences that were decided to stay: the steps that a combination
 * has to contain, joined with plus signs, then a tab and the reason.</p>
 */
@Issue('https://github.com/apache/grails-core/issues/16762')
class AliasMatrixSpec extends HibernateGormDatastoreSpec {

    static final Map<String, Map> STEPS = [
        A1 : [op: 'alias', path: 'likes', name: 'l'],
        A2 : [op: 'alias', path: 'likes', name: 'l', left: true],
        A3 : [op: 'alias', path: 'favorite', name: 'f'],
        A4 : [op: 'alias', path: 'tag', name: 'tag'],
        A5 : [op: 'alias', path: 'likes', name: 'likes'],
        A6 : [op: 'alias', path: 'likes.tag', name: 't'],
        A7 : [op: 'alias', path: 'favorite.tag', name: 't'],
        A8 : [op: 'alias', path: 'tag', name: 't'],
        A9 : [op: 'alias', path: 'favorite', name: 'favorite', left: true],
        R1 : [op: 'eq', prop: 'tag.name', val: 'a'],
        R2 : [op: 'eq', prop: 'l.label', val: 'x'],
        R3 : [op: 'eq', prop: 'l.tag.name', val: 'a'],
        R4 : [op: 'eq', prop: 'f.label', val: 'y'],
        R5 : [op: 'eq', prop: 'tag.name', val: 'b'],
        R6 : [op: 'eq', prop: 't.name', val: 'a'],
        R7 : [op: 'eq', prop: 'likes.label', val: 'x'],
        R8 : [op: 'isNull', prop: 'favorite'],
        R9 : [op: 'isNull', prop: 'tag'],
        R10: [op: 'eqProp', prop: 'l.label', other: 'f.label'],
        R11: [op: 'eq', prop: 'favorite.tag.name', val: 'b'],
        R12: [op: 'eq', prop: 'tag.name', val: 'a', not: true],
        B1 : [op: 'block', assoc: 'likes', body: [[op: 'eq', prop: 'label', val: 'x']]],
        B2 : [op: 'block', assoc: 'likes', body: [[op: 'eq', prop: 'tag.name', val: 'a']]],
        B3 : [op: 'block', assoc: 'favorite', body: [[op: 'eq', prop: 'label', val: 'y']]],
        B4 : [op: 'block', assoc: 'tag', body: [[op: 'eq', prop: 'name', val: 'a']]],
        B5 : [op: 'block', assoc: 'favorite', body: [[op: 'block', assoc: 'tag', body: [[op: 'eq', prop: 'name', val: 'b']]]]],
        B6 : [op: 'block', assoc: 'likes', body: [[op: 'block', assoc: 'tag', body: [[op: 'eq', prop: 'name', val: 'a']]]]],
        B7 : [op: 'block', assoc: 'tag', body: [[op: 'eq', prop: 'name', val: 'b']]],
        B8 : [op: 'block', assoc: 'likes', body: [[op: 'isNull', prop: 'tag']]],
        B9 : [op: 'block', assoc: 'favorite', body: [[op: 'isNull', prop: 'tag']]],
        B10: [op: 'block', assoc: 'favorite', left: true, body: [[op: 'isNull', prop: 'label']]],
        B11: [op: 'block', assoc: 'tag', left: true, body: [[op: 'isNull', prop: 'name']]],
        B12: [op: 'block', assoc: 'likes', body: [[op: 'eq', prop: 'label', val: 'y'], [op: 'eq', prop: 'tag.name', val: 'b']]],
        B13: [op: 'block', assoc: 'favorite', body: [[op: 'eqProp', prop: 'label', other: 'l.label']]],
        B14: [op: 'block', assoc: 'tag', body: [[op: 'eqProp', prop: 'name', other: 't.name']]],
    ]

    void setupSpec() {
        manager.registerDomainClasses(AliasMatrixTeam, AliasMatrixLike, AliasMatrixTag)
    }

    void 'every combination of alias, block and path returns the rows of the reference'() {
        given:
        seed()
        Map<String, String> actual = new TreeMap<>()
        cases().each { String id, List<String> steps ->
            ['root', 'detached', 'subquery'].each { String scope -> actual["${scope}:${id}".toString()] = run(scope, steps) }
        }
        String recordTo = System.getenv('ALIAS_MATRIX_RECORD')

        when:
        if (recordTo) {
            new File(recordTo).text = actual.collect { k, v -> "${k}\t${v}" }.join('\n') + '\n'
        }
        Map<String, String> expected = load('/alias-matrix.expected.txt')
        Map<String, String> accepted = load('/alias-matrix.accepted.txt')
        List<String> differences = recordTo ? [] : actual.findAll { k, v ->
            !isAccepted(k, accepted) && !expected[k]?.startsWith('EXCEPTION') && expected[k] != v
        }.collect { k, v -> "${k}\n    expected ${describe(expected[k])}\n    actual   ${describe(v)}".toString() }
        String report = differences.isEmpty() ? '' : "${differences.size()} of ${actual.size()} combinations differ:\n" + differences.take(40).join('\n')

        then:
        report == ''
    }

    private Map<String, List<String>> cases() {
        List<String> ids = STEPS.keySet().toList()
        Map<String, List<String>> cases = new LinkedHashMap<>()
        ids.each { cases[it] = [it] }
        ids.each { a -> ids.each { b -> if (a != b) { cases["${a}+${b}".toString()] = [a, b] } } }
        Random random = new Random(42)
        while (cases.size() < 1000 + ids.size() * ids.size()) {
            List<String> picked = (0..2).collect { ids[random.nextInt(ids.size())] }
            if (picked.toSet().size() == 3) {
                cases[picked.join('+')] = picked
            }
        }
        cases
    }

    private String run(String scope, List<String> stepIds) {
        List<Map> steps = stepIds.collect { STEPS[it] }
        try {
            List<String> names
            switch (scope) {
                case 'root':
                    names = AliasMatrixTeam.withCriteria(criteria(steps))*.name
                    break
                case 'detached':
                    names = new DetachedCriteria(AliasMatrixTeam).build(criteria(steps)).list()*.name
                    break
                default:
                    DetachedCriteria inner = new DetachedCriteria(AliasMatrixTeam).build(criteria(steps, true))
                    names = AliasMatrixTeam.withCriteria { 'in'('id', inner) }*.name
            }
            encode(names)
        } catch (Throwable t) {
            manager.session.clear()
            "EXCEPTION ${t.getClass().simpleName}${System.getenv('ALIAS_MATRIX_DEBUG') ? ' ' + t.message?.readLines()?.first()?.take(160) : ''}".toString()
        }
    }

    private Closure criteria(List<Map> steps, boolean projectId = false) {
        { def argument = null ->
            def builder = delegate ?: argument
            steps.each { Map step -> apply(builder, step) }
            if (projectId) {
                builder.projections { property('id') }
            }
        }
    }

    private void apply(def builder, Map step) {
        switch (step.op) {
            case 'alias':
                if (step.left) {
                    builder.createAlias(step.path, step.name, 1)
                } else {
                    builder.createAlias(step.path, step.name)
                }
                break
            case 'eq':
                if (step.not) {
                    builder.not { eq(step.prop, step.val) }
                } else {
                    builder.eq(step.prop, step.val)
                }
                break
            case 'isNull':
                builder.isNull(step.prop)
                break
            case 'eqProp':
                builder.eqProperty(step.prop, step.other)
                break
            default:
                Closure body = { def argument = null ->
                    def inner = delegate ?: argument
                    step.body.each { Map child -> apply(inner, child) }
                }
                if (step.left) {
                    builder."${step.assoc}"(1, body)
                } else {
                    builder."${step.assoc}"(body)
                }
        }
    }

    private static boolean isAccepted(String id, Map<String, String> accepted) {
        Set<String> steps = id.substring(id.indexOf(':') + 1).tokenize('+').toSet()
        accepted.keySet().any { String rule -> steps.containsAll(rule.tokenize('+')) }
    }

    private List<String> teamNames = []

    private String encode(List<String> names) {
        BigInteger bits = BigInteger.ZERO
        names.toSet().each { String name -> bits = bits.setBit(teamNames.indexOf(name)) }
        "R${bits.toString(16)}".toString()
    }

    private String describe(String encoded) {
        if (!encoded?.startsWith('R')) {
            return encoded
        }
        BigInteger bits = new BigInteger(encoded.substring(1), 16)
        List<String> names = teamNames.findAll { bits.testBit(teamNames.indexOf(it)) }
        "${names.size()} teams ${names.take(6)}${names.size() > 6 ? '...' : ''}".toString()
    }

    private void seed() {
        AliasMatrixTag a = new AliasMatrixTag(name: 'a').save(failOnError: true)
        AliasMatrixTag b = new AliasMatrixTag(name: 'b').save(failOnError: true)
        List<AliasMatrixLike> likes = [
            new AliasMatrixLike(label: 'x', tag: a).save(failOnError: true),
            new AliasMatrixLike(label: 'y', tag: b).save(failOnError: true),
            new AliasMatrixLike(label: 'x').save(failOnError: true),
        ]
        List<AliasMatrixTag> tags = [null, a, b]
        List<AliasMatrixLike> favorites = [null] + likes
        int n = 0
        tags.eachWithIndex { AliasMatrixTag tag, int ti ->
            favorites.eachWithIndex { AliasMatrixLike favorite, int fi ->
                (0..7).each { int mask ->
                    AliasMatrixTeam team = new AliasMatrixTeam(name: "t${ti}${fi}${mask}".toString(), tag: tag, favorite: favorite)
                    likes.eachWithIndex { AliasMatrixLike like, int li -> if ((mask & (1 << li)) != 0) { team.addToLikes(like) } }
                    team.save(failOnError: true)
                    teamNames << team.name
                    n++
                }
            }
        }
        AliasMatrixTeam.withSession { it.flush(); it.clear() }
    }

    private Map<String, String> load(String resource) {
        InputStream stream = getClass().getResourceAsStream(resource)
        Map<String, String> rows = [:]
        stream?.withReader('UTF-8') { Reader r -> r.eachLine { String line -> if (line.contains('\t')) { rows[line.substring(0, line.indexOf('\t'))] = line.substring(line.indexOf('\t') + 1) } } }
        rows
    }
}

@Entity
class AliasMatrixTeam {
    String name
    AliasMatrixLike favorite
    AliasMatrixTag tag

    static hasMany = [likes: AliasMatrixLike]

    static constraints = {
        favorite nullable: true
        tag nullable: true
    }
}

@Entity
class AliasMatrixLike {
    String label
    AliasMatrixTag tag

    static constraints = {
        tag nullable: true
    }
}

@Entity
class AliasMatrixTag {
    String name
}
