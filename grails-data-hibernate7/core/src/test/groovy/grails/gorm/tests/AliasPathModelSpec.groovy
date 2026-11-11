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

import jakarta.persistence.criteria.JoinType
import org.hibernate.resource.jdbc.spi.StatementInspector
import spock.lang.Issue
import spock.lang.Shared

import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity

/**
 * Checks every combination of aliases, association blocks, dotted paths and junctions against one rule that is
 * stated here and not taken from another implementation: a criteria joins each association path of the query once,
 * whichever way the path is reached, and the join is an inner join unless every declaration of the path asks for a
 * left join. The rows of a combination are the clubs for which one choice of an element for each joined path
 * satisfies the restrictions. The same rows are expected from a query, from a detached criteria and from a subquery.
 */
@Issue('https://github.com/apache/grails-core/issues/16762')
class AliasPathModelSpec extends HibernateGormDatastoreSpec {

    static final Map<String, Map> ATOMS = [
        AL2 : [op: 'alias', path: 'team.likes', name: 'l'],
        AL2L: [op: 'alias', path: 'team.likes', name: 'l', left: true],
        AL3 : [op: 'alias', path: 'team.likes.tag', name: 't'],
        AL3L: [op: 'alias', path: 'team.likes.tag', name: 't', left: true],
        AL4 : [op: 'alias', path: 'team.favorite', name: 'f'],
        AL5 : [op: 'alias', path: 'team.favorite.tag', name: 'ft'],
        AL5L: [op: 'alias', path: 'team.favorite.tag', name: 'ft', left: true],
        AL6 : [op: 'alias', path: 'team.tag', name: 'tt'],
        AL7 : [op: 'alias', path: 'team', name: 'tm'],
        D1  : [op: 'eq', ref: 'team.likes.label', val: 'x'],
        D2  : [op: 'eq', ref: 'team.likes.tag.name', val: 'a'],
        D3  : [op: 'eq', ref: 'team.favorite.label', val: 'y'],
        D4  : [op: 'eq', ref: 'team.favorite.tag.name', val: 'b'],
        D5  : [op: 'eq', ref: 'team.tag.name', val: 'a'],
        D6  : [op: 'isNull', ref: 'team.likes.tag'],
        D7  : [op: 'isNull', ref: 'team.favorite.tag'],
        V1  : [op: 'eq', ref: 'l.label', val: 'x', needs: ['AL2', 'AL2L']],
        V2  : [op: 'eq', ref: 't.name', val: 'a', needs: ['AL3', 'AL3L']],
        V3  : [op: 'eq', ref: 't.name', val: 'b', needs: ['AL3', 'AL3L']],
        V4  : [op: 'eq', ref: 'f.label', val: 'y', needs: ['AL4']],
        V5  : [op: 'eq', ref: 'ft.name', val: 'b', needs: ['AL5', 'AL5L']],
        V6  : [op: 'eq', ref: 'tt.name', val: 'a', needs: ['AL6']],
        V7  : [op: 'eq', ref: 'tm.likes.label', val: 'x', needs: ['AL7']],
        B1  : block('team', [block('likes', [eq('label', 'x')])]),
        B2  : block('team', [block('likes', [block('tag', [eq('name', 'a')])])]),
        B3  : block('team', [block('favorite', [eq('label', 'y')])]),
        B4  : block('team', [block('likes', [isNull('tag')])]),
        B5  : block('team', [block('favorite', [isNull('label')], true)]),
        B6  : block('team', [eq('likes.label', 'x')]),
        B7  : block('team', [[op: 'alias', path: 'likes', name: 'l2'], eq('l2.label', 'y')]),
        B8  : block('team', [block('likes', [block('tag', [isNull('name')], true)])]),
        B9  : block('team', [block('tag', [eq('name', 'a')])]),
        J1  : block('team', [[op: 'and', body: [block('likes', [eq('label', 'x')])]]]),
        J2  : block('team', [[op: 'and', body: [block('likes', [block('tag', [eq('name', 'a')])])]]]),
        J4  : block('team', [[op: 'and', body: [block('likes', [isNull('tag')])]]]),
        J6  : block('team', [[op: 'and', body: [eq('likes.label', 'x')]]]),
        J7  : block('team', [[op: 'or', body: [eq('likes.label', 'x'), isNull('favorite')]]]),
        T1  : [op: 'and', body: [block('team', [block('likes', [eq('label', 'x')])])]],
        T4  : [op: 'and', body: [block('team', [block('likes', [isNull('tag')])])]],
        O1  : [op: 'or', body: [eq('team.likes.label', 'x'), eq('team.favorite.label', 'y')]],
        O2  : [op: 'or', body: [eq('team.likes.label', 'x'), isNull('team.favorite.tag')]],
        K1  : [op: 'ne', ref: 'team.likes.label', val: 'x'],
        K2  : [op: 'in', ref: 'team.likes.label', vals: ['x', 'z']],
        K3  : [op: 'like', ref: 'team.likes.label', val: 'x%'],
        K4  : [op: 'between', ref: 'team.likes.label', from: 'x', to: 'y'],
        K5  : [op: 'gt', ref: 'team.likes.label', val: 'x'],
        K6  : [op: 'le', ref: 'team.favorite.label', val: 'x'],
        K7  : [op: 'isNotNull', ref: 'team.likes.tag'],
        K8  : [op: 'eqProp', ref: 'team.likes.label', other: 'team.favorite.label'],
        K9  : [op: 'neProp', ref: 'team.likes.tag.name', other: 'team.favorite.tag.name'],
        K10 : [op: 'not', body: [eq('team.likes.label', 'x')]],
        K11 : [op: 'sizeEq', ref: 'team.likes', n: 2],
        K12 : [op: 'isEmpty', ref: 'team.likes'],
        K13 : [op: 'isNotEmpty', ref: 'team.likes'],
        K14 : [op: 'not', body: [isNull('team.favorite.tag')]],
        K15 : [op: 'ge', ref: 'team.favorite.tag.name', val: 'b'],
        K16 : [op: 'lt', ref: 'team.likes.tag.name', val: 'b'],
        BK1 : block('team', [block('likes', [[op: 'in', ref: 'label', vals: ['x', 'z']]])]),
        BK2 : block('team', [block('likes', [[op: 'between', ref: 'label', from: 'x', to: 'y']])]),
        BK3 : block('team', [block('likes', [[op: 'not', body: [eq('label', 'x')]]])]),
        BK4 : block('team', [[op: 'ne', ref: 'likes.label', val: 'x']]),
        BK5 : block('team', [[op: 'eqProp', ref: 'likes.label', other: 'favorite.label']]),
        BK6 : block('team', [[op: 'sizeEq', ref: 'likes', n: 1]]),
        BK7 : block('team', [block('favorite', [[op: 'like', ref: 'label', val: 'y%']], true)]),
    ]

    private static Map eq(String ref, String val) {
        [op: 'eq', ref: ref, val: val]
    }

    private static Map isNull(String ref) {
        [op: 'isNull', ref: ref]
    }

    private static Map block(String assoc, List<Map> body, boolean left = false) {
        [op: 'block', assoc: assoc, left: left, body: body]
    }

    @Shared
    ModelSqlCapture sqlCapture = new ModelSqlCapture()

    void setupSpec() {
        manager.grailsConfig.hibernate.session_factory.statement_inspector = sqlCapture
        manager.registerDomainClasses(AliasModelClub, AliasMatrixTeam, AliasMatrixLike, AliasMatrixTag)
    }

    private List<Map> clubs = []

    void 'every combination returns the rows of the one-join-per-path rule'() {
        given:
        seed()
        List<String> differences = []
        int total = 0
        cases().each { String id, List<String> ids ->
            List<Map> steps = ids.collect { ATOMS[it] }
            Set<String> expected = expectedClubs(steps)
            ['root', 'detached', 'subquery'].each { String scope ->
                total++
                sqlCapture.statements.clear()
                Set<String> actual = run(scope, steps)
                if (actual != expected) {
                    if (System.getenv('ALIAS_MODEL_ONLY')) {
                        System.err.println("SQL ${scope}:${id} " + sqlCapture.statements.findAll { it.contains('alias_model_club') && it.startsWith('select') }.last())
                    }
                    differences << "${scope}:${id}\n    expected ${describe(expected)}\n    actual   ${describe(actual)}".toString()
                }
            }
        }
        String recordTo = System.getenv('ALIAS_MODEL_REPORT')
        if (recordTo) {
            new File(recordTo).text = differences.join('\n') + '\n'
        }

        expect:
        differences.isEmpty()
    }

    private Map<String, List<String>> cases() {
        List<String> ids = ATOMS.keySet().toList()
        Map<String, List<String>> cases = new LinkedHashMap<>()
        ids.each { cases[it] = [it] }
        ids.each { a -> ids.each { b -> if (a != b) { cases["${a}+${b}".toString()] = [a, b] } } }
        Random random = new Random((System.getenv('ALIAS_MODEL_SEED') ?: '7') as long)
        int target = cases.size() + ((System.getenv('ALIAS_MODEL_RANDOM') ?: '2500') as int)
        while (cases.size() < target) {
            List<String> picked = (0..2).collect { ids[random.nextInt(ids.size())] }
            if (picked.toSet().size() == 3) {
                cases[picked.join('+')] = picked
            }
        }
        String only = System.getenv('ALIAS_MODEL_ONLY')
        cases.findAll { String id, List<String> picked -> valid(picked) && (!only || id in only.tokenize(',')) }
    }

    private boolean valid(List<String> picked) {
        Set<String> seen = []
        Set<String> declared = []
        for (String id : picked) {
            Map atom = ATOMS[id]
            if (atom.needs && !atom.needs.any { it in seen }) {
                return false
            }
            seen << id
        }
        List<String> paths = []
        picked.each { id -> declarations(ATOMS[id], '', paths) }
        paths.toSet().size() == paths.size()
    }

    private void declarations(Map node, String scope, List<String> out) {
        switch (node.op) {
            case 'alias':
                out << join(scope, node.path)
                break
            case 'block':
                String path = join(scope, node.assoc)
                out << path
                node.body.each { declarations(it as Map, path, out) }
                break
            case 'and':
            case 'or':
            case 'not':
                node.body.each { declarations(it as Map, scope, out) }
                break
        }
    }

    private static String join(String scope, String path) {
        scope ? "${scope}.${path}".toString() : path
    }

    private Set<String> expectedClubs(List<Map> steps) {
        Map<String, String> aliases = [:]
        steps.each { collectAliases(it, '', aliases) }
        Map<String, String> kinds = [:]
        steps.each { collectJoins(it, '', aliases, kinds) }
        List<String> paths = kinds.keySet().findAll { !it.startsWith('below:') }.toList()
        kinds.keySet().findAll { it.startsWith('below:') }.each { String key -> String path = key.substring(6); if (!paths.contains(path)) { paths << path } }
        paths = paths.sort { it.count('.') }
        clubs.findAll { Map club -> matches(club, steps, aliases, paths, kinds) }*.name as Set
    }

    private void collectAliases(Map node, String scope, Map<String, String> aliases) {
        switch (node.op) {
            case 'alias':
                aliases[node.name as String] = join(scope, node.path as String)
                break
            case 'block':
                node.body.each { collectAliases(it as Map, join(scope, node.assoc as String), aliases) }
                break
            case 'and':
            case 'or':
            case 'not':
                node.body.each { collectAliases(it as Map, scope, aliases) }
                break
        }
    }

    private String absolute(String ref, String scope, Map<String, String> aliases) {
        List<String> segments = ref.tokenize('.')
        segments.size() > 1 && aliases.containsKey(segments[0]) ?
                ([aliases[segments[0]]] + segments.drop(1)).join('.') : join(scope, ref)
    }

    private void mention(Map<String, String> kinds, String path, String kind) {
        if (!path) {
            return
        }
        String own = kind == 'implicit' ? 'implicit' : kind
        merge(kinds, path, own)
        List<String> parts = path.tokenize('.')
        (1..<parts.size()).each { int n ->
            merge(kinds, 'below:' + parts.take(n).join('.'), kind == 'implicit' ? 'inner' : kind)
        }
    }

    private static void merge(Map<String, String> kinds, String key, String kind) {
        String known = kinds[key]
        if (known == null || (known == 'left' && kind == 'inner') || (known == 'implicit' && kind != 'implicit')) {
            kinds[key] = kind
        }
    }

    private static String typeOf(Map<String, String> kinds, String path) {
        String own = kinds[path]
        own == 'implicit' ? 'inner' : own ?: kinds['below:' + path]
    }

    private void collectJoins(Map node, String scope, Map<String, String> aliases, Map<String, String> kinds) {
        switch (node.op) {
            case 'alias':
                mention(kinds, join(scope, node.path as String), node.left ? 'left' : 'inner')
                break
            case 'block':
                String path = join(scope, node.assoc as String)
                mention(kinds, path, node.left ? 'left' : 'inner')
                node.body.each { collectJoins(it as Map, path, aliases, kinds) }
                break
            case 'and':
            case 'or':
                node.body.each { collectJoins(it as Map, scope, aliases, kinds) }
                break
            case 'not':
                node.body.each { collectJoins(it as Map, scope, aliases, kinds) }
                break
            default:
                mentionRef(node.ref as String, scope, aliases, kinds)
                if (node.other) {
                    mentionRef(node.other as String, scope, aliases, kinds)
                }
        }
    }

    private void mentionRef(String ref, String scope, Map<String, String> aliases, Map<String, String> kinds) {
        String path = absolute(ref, scope, aliases)
        String holder = path.contains('.') ? path.substring(0, path.lastIndexOf('.')) : ''
        if (holder != scope && !isAliasOnly(ref, aliases)) {
            mention(kinds, holder, 'implicit')
        }
    }

    private boolean isAliasOnly(String ref, Map<String, String> aliases) {
        List<String> segments = ref.tokenize('.')
        segments.size() == 2 && aliases.containsKey(segments[0])
    }

    private boolean matches(Map club, List<Map> steps, Map<String, String> aliases, List<String> paths, Map<String, String> kinds) {
        bind(club, paths, 0, [:], kinds) { Map<String, Object> binding ->
            and(steps.collect { holds(it, '', aliases, binding, club) }) == Boolean.TRUE
        }
    }

    private boolean bind(Map club, List<String> paths, int index, Map<String, Object> binding, Map<String, String> kinds, Closure test) {
        if (index == paths.size()) {
            return test(binding)
        }
        String path = paths[index]
        String parent = path.contains('.') ? path.substring(0, path.lastIndexOf('.')) : ''
        Object parentElement = parent ? binding[parent] : club
        String name = path.substring(path.lastIndexOf('.') + 1)
        List candidates = parentElement == null ? [null] : elements(parentElement, name)
        if (candidates.isEmpty() || candidates == [null]) {
            candidates = typeOf(kinds, path) == 'left' ? [null] : []
        }
        candidates.any { Object element ->
            binding[path] = element
            bind(club, paths, index + 1, binding, kinds, test)
        }
    }

    private List elements(Object owner, String name) {
        def value = ((Map) owner)[name]
        value == null ? [] : value instanceof List ? (List) value : [value]
    }

    private static Boolean and(List<Boolean> values) {
        values.any { it == Boolean.FALSE } ? Boolean.FALSE : values.any { it == null } ? null : Boolean.TRUE
    }

    private static Boolean or(List<Boolean> values) {
        values.any { it == Boolean.TRUE } ? Boolean.TRUE : values.any { it == null } ? null : Boolean.FALSE
    }

    private static Boolean not(Boolean value) {
        value == null ? null : !value
    }

    private Object leaf(String ref, String scope, Map<String, String> aliases, Map<String, Object> binding, Map club) {
        String path = absolute(ref, scope, aliases)
        String holder = path.contains('.') ? path.substring(0, path.lastIndexOf('.')) : ''
        String name = path.substring(path.lastIndexOf('.') + 1)
        Map element = (holder ? binding[holder] : club) as Map
        element == null ? null : element[name]
    }

    private Boolean holds(Map node, String scope, Map<String, String> aliases, Map<String, Object> binding, Map club) {
        switch (node.op) {
            case 'alias':
                return Boolean.TRUE
            case 'block':
                String path = join(scope, node.assoc as String)
                return and(node.body.collect { holds(it as Map, path, aliases, binding, club) })
            case 'and':
                return and(node.body.collect { holds(it as Map, scope, aliases, binding, club) })
            case 'or':
                return or(node.body.collect { holds(it as Map, scope, aliases, binding, club) })
            case 'not':
                return not(and(node.body.collect { holds(it as Map, scope, aliases, binding, club) }))
            case 'isNull':
                return leaf(node.ref as String, scope, aliases, binding, club) == null
            case 'isNotNull':
                return leaf(node.ref as String, scope, aliases, binding, club) != null
            case 'isEmpty':
            case 'isNotEmpty':
            case 'sizeEq':
                List values = leaf(node.ref as String, scope, aliases, binding, club) as List
                if (values == null) {
                    return null
                }
                return node.op == 'isEmpty' ? values.isEmpty() : node.op == 'isNotEmpty' ? !values.isEmpty() : values.size() == node.n
            case 'eqProp':
            case 'neProp':
                def left = leaf(node.ref as String, scope, aliases, binding, club)
                def right = leaf(node.other as String, scope, aliases, binding, club)
                return left == null || right == null ? null : (node.op == 'eqProp') == (left == right)
            default:
                def value = leaf(node.ref as String, scope, aliases, binding, club)
                if (node.op == 'ne') {
                    // ne is x <> value or x is null, as it is on Hibernate 5, so a missing element matches it
                    return value == null || value != node.val
                }
                if (value == null) {
                    return null
                }
                switch (node.op) {
                    case 'eq': return value == node.val
                    case 'gt': return value > node.val
                    case 'ge': return value >= node.val
                    case 'lt': return value < node.val
                    case 'le': return value <= node.val
                    case 'in': return value in node.vals
                    case 'like': return value.startsWith((node.val as String).replace('%', ''))
                    case 'between': return value >= node.from && value <= node.to
                    default: throw new IllegalArgumentException("the model has no rule for ${node.op}")
                }
        }
    }

    private Set<String> run(String scope, List<Map> steps) {
        try {
            List<String> names
            switch (scope) {
                case 'root':
                    names = AliasModelClub.withCriteria(criteria(steps))*.name
                    break
                case 'detached':
                    names = new DetachedCriteria(AliasModelClub).build(criteria(steps)).list()*.name
                    break
                default:
                    DetachedCriteria inner = new DetachedCriteria(AliasModelClub).build(criteria(steps, true))
                    names = AliasModelClub.withCriteria { 'in'('id', inner) }*.name
            }
            names as Set
        } catch (Throwable t) {
            manager.session.clear()
            if (System.getenv('ALIAS_MODEL_ONLY')) {
                t.printStackTrace()
            }
            ["EXCEPTION ${t.getClass().simpleName} ${t.message?.readLines()?.first()?.take(120)}".toString()] as Set
        }
    }

    private Closure criteria(List<Map> steps, boolean projectId = false) {
        { def argument = null ->
            def builder = delegate != null ? delegate : argument
            steps.each { Map step -> apply(builder, step, '', builder) }
            if (projectId) {
                builder.projections { property('id') }
            }
        }
    }

    private void apply(def builder, Map step, String scope, def root) {
        switch (step.op) {
            case 'alias':
                if (step.left && builder instanceof DetachedCriteria) {
                    builder.createAlias(step.path, step.name)
                    builder.join(step.path, JoinType.LEFT)
                } else if (step.left) {
                    builder.createAlias(step.path, step.name, 1)
                } else {
                    builder.createAlias(step.path, step.name)
                }
                break
            case 'eq':
                builder.eq(step.ref, step.val)
                break
            case 'ne':
                builder.ne(step.ref, step.val)
                break
            case 'gt':
                builder.gt(step.ref, step.val)
                break
            case 'ge':
                builder.ge(step.ref, step.val)
                break
            case 'lt':
                builder.lt(step.ref, step.val)
                break
            case 'le':
                builder.le(step.ref, step.val)
                break
            case 'in':
                builder.'in'(step.ref, step.vals)
                break
            case 'like':
                builder.like(step.ref, step.val)
                break
            case 'between':
                builder.between(step.ref, step.from, step.to)
                break
            case 'isNull':
                builder.isNull(step.ref)
                break
            case 'isNotNull':
                builder.isNotNull(step.ref)
                break
            case 'eqProp':
                builder.eqProperty(step.ref, step.other)
                break
            case 'neProp':
                builder.neProperty(step.ref, step.other)
                break
            case 'sizeEq':
                builder.sizeEq(step.ref, step.n)
                break
            case 'isEmpty':
                builder.isEmpty(step.ref)
                break
            case 'isNotEmpty':
                builder.isNotEmpty(step.ref)
                break
            case 'not':
                builder.not(inner(step, scope, root))
                break
            case 'and':
                builder.and(inner(step, scope, root))
                break
            case 'or':
                builder.or(inner(step, scope, root))
                break
            default:
                String path = join(scope, step.assoc as String)
                if (step.left && root instanceof DetachedCriteria) {
                    root.join(path, JoinType.LEFT)
                    builder."${step.assoc}"(inner(step, path, root))
                } else if (step.left) {
                    builder."${step.assoc}"(1, inner(step, path, root))
                } else {
                    builder."${step.assoc}"(inner(step, path, root))
                }
        }
    }

    private Closure inner(Map step, String scope, def root) {
        { def argument = null ->
            def nested = delegate != null ? delegate : argument
            step.body.each { Map child -> apply(nested, child, scope, root) }
        }
    }

    private String describe(Set<String> names) {
        names.size() > 4 ? "${names.size()} clubs" : names.toString()
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
        tags.eachWithIndex { AliasMatrixTag tag, int ti ->
            favorites.eachWithIndex { AliasMatrixLike favorite, int fi ->
                (0..7).each { int mask ->
                    AliasMatrixTeam team = new AliasMatrixTeam(name: "t${ti}${fi}${mask}".toString(), tag: tag, favorite: favorite)
                    List<AliasMatrixLike> members = []
                    likes.eachWithIndex { AliasMatrixLike like, int li -> if ((mask & (1 << li)) != 0) { members << like } }
                    members.each { team.addToLikes(it) }
                    team.save(failOnError: true)
                    new AliasModelClub(name: "c${team.name}".toString(), team: team).save(failOnError: true)
                    clubs << [name: "c${team.name}".toString(), team: [name: team.name, tag: plain(tag), favorite: plain(favorite), likes: members.collect { plain(it) }]]
                }
            }
        }
        new AliasModelClub(name: 'cnone').save(failOnError: true)
        clubs << [name: 'cnone', team: null]
        AliasModelClub.withSession { it.flush(); it.clear() }
    }

    private static Map plain(Object value) {
        if (value == null) {
            return null
        }
        value instanceof AliasMatrixTag ? [name: value.name] : [label: value.label, tag: plain(value.tag)]
    }
}

@Entity
class AliasModelClub {
    String name
    AliasMatrixTeam team

    static constraints = {
        team nullable: true
    }
}

class ModelSqlCapture implements StatementInspector {
    List<String> statements = []

    @Override
    String inspect(String sql) {
        statements.add(sql)
        sql
    }
}
