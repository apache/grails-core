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
package org.grails.datastore.gorm.mongo

import grails.gorm.DetachedCriteria
import grails.persistence.Entity
import org.apache.grails.data.mongo.core.GrailsDataMongoTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.bson.Document

/**
 * Every combination of the state of the embedded values of an owner, a negated condition on a property of an embedded
 * value (one and two levels deep, stored under other names than the properties), the place of that condition in the
 * query, the way the query is written and what it returns. The expected rows come from a three-valued evaluation of the
 * same condition over the stored documents: a condition on an embedded value that is not there is unknown, and so is its
 * negation, as it is where the value is a join; a property of a value that is there, but is null or missing, is not equal
 * to anything, as MongoDB treats a plain property. The same conditions are run through an association to the owner, and
 * on a collection of embedded values, where a block holds when one element satisfies it.
 */
class EmbeddedNegationMatrixSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {

    private static final Map<String, String> STORED = [address: 'addr', geo: 'g', city: 'town', zone: 'zn', places: 'pl']

    private static final List<Map> OWNERS = [
        [name: 'Nina', doc: [:]],
        [name: 'AddrNull', doc: [addr: null]],
        [name: 'AddrEmpty', doc: [addr: [:]]],
        [name: 'NoCity', doc: [addr: [g: [zn: 'north']]]],
        [name: 'CityNull', doc: [addr: [town: null, g: [zn: 'north']]]],
        [name: 'Amelie', doc: [addr: [town: 'Paris', g: [zn: 'north']]]],
        [name: 'Bob', doc: [addr: [town: 'Rome', g: [zn: 'south']]]],
        [name: 'ParisSouth', doc: [addr: [town: 'Paris', g: [zn: 'south']]]],
        [name: 'RomeNoGeo', doc: [addr: [town: 'Rome']]],
        [name: 'RomeGeoEmpty', doc: [addr: [town: 'Rome', g: [:]]]],
        [name: 'RomeGeoNull', doc: [addr: [town: 'Rome', g: null]]],
        [name: 'RomeZoneNull', doc: [addr: [town: 'Rome', g: [zn: null]]]],
    ]

    private static final List<Map> TEAMS = [
        [name: 'Nina', doc: [:]],
        [name: 'PlNull', doc: [pl: null]],
        [name: 'PlEmpty', doc: [pl: []]],
        [name: 'Paris', doc: [pl: [[town: 'Paris']]]],
        [name: 'Bob', doc: [pl: [[town: 'Rome']]]],
        [name: 'Both', doc: [pl: [[town: 'Paris'], [town: 'Rome']]]],
        [name: 'CityNull', doc: [pl: [[town: null]]]],
        [name: 'EmptyDoc', doc: [pl: [[:]]]],
        [name: 'ParisAndNull', doc: [pl: [[town: 'Paris'], [town: null]]]],
    ]

    void setupSpec() {
        manager.domainClasses.addAll([SnOwner, SnCity, SnClub, SnTeam])
    }

    void 'every generated negated condition returns the rows the three-valued evaluation expects'() {
        given:
        SnOwner.DB.getCollection('sn_owners').with { deleteMany(new Document()); OWNERS.each { insertOne(document([name: it.name] + (Map) it.doc)) } }
        SnTeam.DB.getCollection('sn_teams').with { deleteMany(new Document()); TEAMS.each { insertOne(document([name: it.name] + (Map) it.doc)) } }
        ['Paris', 'Oslo', 'Rome', 'north'].each { new SnCity(name: it).save(flush: true, failOnError: true) }
        manager.session.clear()
        OWNERS.each { new SnClub(name: it.name, head: SnOwner.findByName((String) it.name)).save(flush: true, validate: false) }
        manager.session.clear()
        List<String> mismatches = []

        when:
        List<List> ownerTrees = ownerTrees()
        List<List> teamTrees = teamTrees()
        List<List> targets = [
            ['owner', SnOwner, OWNERS, ownerTrees, [['criteria', 'list'], ['criteria', 'count'], ['detached', 'list'], ['detached', 'count'], ['where', 'list'], ['findAll', 'list']]],
            ['club', SnClub, OWNERS, ownerTrees, [['criteria', 'list'], ['detached', 'list']]],
            ['team', SnTeam, TEAMS, teamTrees, [['criteria', 'list'], ['criteria', 'count'], ['detached', 'list']]],
        ]
        targets.each { List target ->
            String kind = target[0]
            Class domain = (Class) target[1]
            List<Map> rows = (List<Map>) target[2]
            ((List<List>) target[3]).each { List tree ->
                List<String> expected = rows.findAll { eval(tree, [name: it.name] + (Map) it.doc, it) == Boolean.TRUE }*.name.sort()
                ((List<List>) target[4]).each { List way ->
                    String entry = way[0]
                    String terminal = way[1]
                    if (entry in ['where', 'findAll'] && whereSource(tree, []) == null) {
                        return
                    }
                    String problem
                    try {
                        Object found = execute(entry, terminal, kind, domain, tree)
                        problem = compare(terminal, found, expected)
                    } catch (Throwable t) {
                        problem = refused(kind, entry, t) ? null : 'THROWS ' + t.class.simpleName + ': ' + t.message?.replace('\n', ' ')?.take(300)
                    }
                    if (problem != null) {
                        mismatches << "${kind}/${entry}/${terminal} ${tree} ${problem}".toString()
                    }
                }
            }
        }

        then:
        mismatches.isEmpty()
    }

    private static Object execute(String entry, String terminal, String kind, Class domain, List tree) {
        List wrapped = kind == 'club' ? ['block', 'head', tree] : tree
        switch (entry) {
            case 'criteria':
                def criteria = domain.createCriteria()
                return terminal == 'list' ? criteria.list { apply(delegate, wrapped) } : criteria.count { apply(delegate, wrapped) }
            case 'detached':
                DetachedCriteria detached = new DetachedCriteria(domain).build { apply(delegate, wrapped) }
                return terminal == 'list' ? detached.list() : detached.count()
            default:
                String source = whereSource(tree, [])
                String call = entry == 'where' ? "SnOwner.where { ${source} }.list()" : "SnOwner.findAll { ${source} }"
                return new GroovyShell(SnOwner.classLoader).evaluate('import org.grails.datastore.gorm.mongo.SnOwner\n' + call)
        }
    }

    /** the two refusals the stores state in words: a query through an association, and a negation of a negation in a where query */
    private static boolean refused(String kind, String entry, Throwable thrown) {
        (kind == 'club' && thrown instanceof UnsupportedOperationException && thrown.message?.contains('Join queries are not supported')) ||
            (entry in ['where', 'findAll'] && thrown.message?.contains('You can only negate a binary expressions in queries'))
    }

    private static String compare(String terminal, Object found, List<String> expected) {
        switch (terminal) {
            case 'count': return found == expected.size() ? null : "count=${found} expected=${expected.size()}".toString()
            default:
                List<String> got = ((List) found)*.name.sort()
                return got == expected ? null : "extra=${got - expected} missing=${expected - got}".toString()
        }
    }

    private static Document document(Map map) {
        Document result = new Document()
        map.each { key, value -> result.append((String) key, convert(value)) }
        result
    }

    private static Object convert(Object value) {
        value instanceof Map ? document((Map) value) : (value instanceof List ? ((List) value).collect { convert(it) } : value)
    }

    private static List<List> positions(List<List> bases) {
        List<List> trees = []
        bases.each { List x ->
            trees << x
            trees << ['and', [['nameNotNull'], x]]
            trees << ['or', [['nameEq', 'Nina'], x]]
            trees << ['not', ['and', [['nameEq', 'Bob'], x]]]
            trees << ['not', ['or', [['nameEq', 'Bob'], x]]]
        }
        trees
    }

    private static List<List> ownerTrees() {
        List<List> bases = []
        [[['address'], 'city', 'Paris'], [['address', 'geo'], 'zone', 'north']].each { List d ->
            List<String> blocks = (List<String>) d[0]
            String leaf = d[1]
            String value = d[2]
            Closure wrap = { List node ->
                List result = node
                blocks.reverse().each { result = ['block', it, result] }
                result
            }
            List eq = ['eq', leaf, value]
            List inList = ['inList', leaf, [value]]
            bases << wrap(['ne', leaf, value])
            bases << wrap(['not', inList])
            bases << wrap(['not', eq])
            bases << wrap(eq)
            bases << wrap(inList)
            bases << ['not', wrap(eq)]
            bases << ['not', wrap(inList)]
            bases << ['not', ['not', wrap(eq)]]
            bases << wrap(['not', ['not', eq]])
        }
        bases << ['block', 'address', ['not', ['block', 'geo', ['eq', 'zone', 'north']]]]
        bases << ['block', 'address', ['notInSub', 'city', ['Paris']]]
        bases << ['block', 'address', ['block', 'geo', ['notInSub', 'zone', ['north']]]]
        List<List> trees = positions(bases)
        List<List> negations1 = [bases[0], bases[1], bases[5]]
        List<List> negations2 = [bases[9], bases[10], bases[14]]
        negations1.each { List a ->
            negations2.each { List b ->
                trees << ['and', [a, b]]
                trees << ['or', [a, b]]
            }
        }
        trees
    }

    private static List<List> teamTrees() {
        List eq = ['eq', 'city', 'Paris']
        List inList = ['inList', 'city', ['Paris']]
        List<List> bases = [
            ['block', 'places', eq],
            ['block', 'places', inList],
            ['block', 'places', ['ne', 'city', 'Paris']],
            ['block', 'places', ['not', inList]],
            ['block', 'places', ['not', eq]],
            ['block', 'places', ['not', ['not', eq]]],
            ['block', 'places', ['notInSub', 'city', ['Paris']]],
            ['not', ['block', 'places', eq]],
            ['not', ['block', 'places', inList]],
            ['not', ['not', ['block', 'places', eq]]],
        ]
        positions(bases)
    }

    private static void apply(Object builder, List node) {
        switch (node[0]) {
            case 'eq': builder.eq((String) node[1], node[2]); break
            case 'ne': builder.ne((String) node[1], node[2]); break
            case 'inList': builder.inList((String) node[1], (List) node[2]); break
            case 'notInSub':
                List values = (List) node[2]
                builder.notIn((String) node[1], new DetachedCriteria(SnCity).build { inList('name', values) }.property('name'))
                break
            case 'nameEq': builder.eq('name', node[1]); break
            case 'nameNotNull': builder.isNotNull('name'); break
            case 'not': builder.not({ -> def d = delegate; apply(d, (List) node[1]) }); break
            case 'and': builder.and({ -> def d = delegate; ((List) node[1]).each { apply(d, (List) it) } }); break
            case 'or': builder.or({ -> def d = delegate; ((List) node[1]).each { apply(d, (List) it) } }); break
            case 'block': builder."${node[1]}"({ -> def d = delegate; apply(d, (List) node[2]) }); break
            default: throw new IllegalStateException('unknown node ' + node)
        }
    }

    /** the same condition as the source of a where query, null for a form the where DSL has no spelling for */
    private static String whereSource(List node, List<String> path) {
        String prefix = path ? path.join('.') + '.' : ''
        switch (node[0]) {
            case 'eq': return "${prefix}${node[1]} == '${node[2]}'".toString()
            case 'ne': return "${prefix}${node[1]} != '${node[2]}'".toString()
            case 'inList': return "${prefix}${node[1]} in [${((List) node[2]).collect { "'${it}'" }.join(', ')}]".toString()
            case 'nameEq': return "name == '${node[1]}'".toString()
            case 'nameNotNull': return 'name != null'
            case 'notInSub': return null
            case 'not':
                String inner = whereSource((List) node[1], path)
                return inner == null ? null : "!(${inner})".toString()
            case 'and':
            case 'or':
                List<String> parts = ((List<List>) node[1]).collect { whereSource(it, path) }
                return parts.contains(null) ? null : '(' + parts.join(node[0] == 'and' ? ' && ' : ' || ') + ')'
            case 'block': return whereSource((List) node[2], path + [(String) node[1]])
            default: throw new IllegalStateException('unknown node ' + node)
        }
    }

    /** true, false or null for unknown; {@code current} is the document the node applies to, null where the embedded value is not there */
    private static Boolean eval(List node, Map current, Map owner) {
        switch (node[0]) {
            case 'eq': return current == null ? null : equalTo(current[STORED[(String) node[1]]], node[2])
            case 'inList': return current == null ? null : (current[STORED[(String) node[1]]] != null && ((List) node[2]).contains(current[STORED[(String) node[1]]]))
            case 'ne': return negate(eval(['eq', node[1], node[2]], current, owner))
            case 'notInSub': return negate(eval(['inList', node[1], node[2]], current, owner))
            case 'nameEq': return owner.name == node[1]
            case 'nameNotNull': return owner.name != null
            case 'not': return negate(eval((List) node[1], current, owner))
            case 'and':
                List<Boolean> parts = ((List<List>) node[1]).collect { eval(it, current, owner) }
                return parts.contains(Boolean.FALSE) ? false : (parts.contains(null) ? null : true)
            case 'or':
                List<Boolean> parts = ((List<List>) node[1]).collect { eval(it, current, owner) }
                return parts.contains(Boolean.TRUE) ? true : (parts.contains(null) ? null : false)
            case 'block':
                Object inner = current == null ? null : current[STORED[(String) node[1]]]
                if (node[1] == 'places') {
                    List elements = inner instanceof List ? (List) inner : []
                    return elements.any { eval((List) node[2], it instanceof Map ? (Map) it : [:], owner) == Boolean.TRUE }
                }
                return eval((List) node[2], inner instanceof Map ? (Map) inner : null, owner)
            default: throw new IllegalStateException('unknown node ' + node)
        }
    }

    private static Boolean equalTo(Object stored, Object value) {
        stored == null ? false : stored == value
    }

    private static Boolean negate(Boolean value) {
        value == null ? null : !value
    }
}

@Entity
class SnClub {

    String id
    String name
    SnOwner head

    static mapWith = 'mongo'

    static mapping = {
        collection 'sn_clubs'
    }
}

@Entity
class SnTeam {

    String id
    String name
    List<SnPlace> places

    static mapWith = 'mongo'

    static hasMany = [places: SnPlace]

    static embedded = ['places']

    static mapping = {
        collection 'sn_teams'
        places field: 'pl'
    }
}

@Entity
class SnPlace {

    String city

    static mapWith = 'mongo'

    static mapping = {
        city field: 'town'
    }
}
