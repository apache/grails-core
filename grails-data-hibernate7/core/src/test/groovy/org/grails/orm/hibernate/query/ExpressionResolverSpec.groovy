/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.orm.hibernate.query

import jakarta.persistence.criteria.From
import jakarta.persistence.criteria.Path
import jakarta.persistence.criteria.Join
import jakarta.persistence.criteria.JoinType
import jakarta.persistence.criteria.Expression
import spock.lang.Specification

/**
 * Unit tests for ExpressionResolver.
 */
class ExpressionResolverSpec extends Specification {

    def "test resolve simple property path"() {
        given:
        def root = Mock(From)
        def propPath = Mock(Path)
        def joinTracker = new JoinTracker(root)
        def aliasRegistry = new AliasRegistry()
        def resolver = new ExpressionResolver(aliasRegistry, joinTracker)

        when:
        def result = resolver.resolve("firstName")

        then:
        1 * root.get("firstName") >> propPath
        result == propPath
    }

    def "test resolve {alias} returns root"() {
        given:
        def root = Mock(From)
        def joinTracker = new JoinTracker(root)
        def resolver = new ExpressionResolver(new AliasRegistry(), joinTracker)

        expect:
        resolver.resolve("{alias}") == root
        resolver.resolve("root") == root
    }

    def "test resolve with alias:property separator"() {
        given:
        def root = Mock(From)
        def faceJoin = Mock(Join)
        def namePath = Mock(Path)
        def aliasRegistry = new AliasRegistry()
        aliasRegistry.define("f", new HibernateAlias("face", "f", JoinType.INNER))
        def joinTracker = new JoinTracker(root)
        def resolver = new ExpressionResolver(aliasRegistry, joinTracker)

        when:
        def result = resolver.resolve("f:name")

        then:
        1 * root.join("face", JoinType.INNER) >> faceJoin
        1 * faceJoin.get("name") >> namePath
        result == namePath
        aliasRegistry.getRealized("f") == faceJoin
        joinTracker.getJoin("f") == faceJoin
    }

    def "test resolve with dot notation alias.property"() {
        given:
        def root = Mock(From)
        def faceJoin = Mock(Join)
        def namePath = Mock(Path)
        def aliasRegistry = new AliasRegistry()
        aliasRegistry.define("f", new HibernateAlias("face", "f", JoinType.INNER))
        def joinTracker = new JoinTracker(root)
        def resolver = new ExpressionResolver(aliasRegistry, joinTracker)

        when:
        def result = resolver.resolve("f.name")

        then:
        1 * root.join("face", JoinType.INNER) >> faceJoin
        1 * faceJoin.get("name") >> namePath
        result == namePath
    }

    def "test resolve using already joined path"() {
        given:
        def root = Mock(From)
        def nicknamesJoin = Mock(Join)
        def joinTracker = new JoinTracker(root)
        joinTracker.addJoin("nicknames", nicknamesJoin)
        def resolver = new ExpressionResolver(new AliasRegistry(), joinTracker)

        expect:
        resolver.resolve("nicknames") == nicknamesJoin
    }

    def "test resolve with parent context (correlated subquery)"() {
        given:
        // Parent context
        def parentRoot = Mock(From)
        def faceJoin = Mock(Join)
        def parentAliasRegistry = new AliasRegistry()
        parentAliasRegistry.define("f", new HibernateAlias("face", "f", JoinType.INNER))
        parentAliasRegistry.realize("f", faceJoin)
        def parentJoinTracker = new JoinTracker(parentRoot)
        parentJoinTracker.addJoin("f", faceJoin)

        // Subquery context
        def subRoot = Mock(From)
        def aliasRegistry = new AliasRegistry(parentAliasRegistry)
        def joinTracker = new JoinTracker(parentJoinTracker, subRoot)
        def resolver = new ExpressionResolver(aliasRegistry, joinTracker)

        def namePath = Mock(Path)

        when:
        def result = resolver.resolve("f.name")

        then:
        0 * subRoot.join(_, _) // Should NOT join on subquery root
        1 * faceJoin.get("name") >> namePath
        result == namePath
    }
}
