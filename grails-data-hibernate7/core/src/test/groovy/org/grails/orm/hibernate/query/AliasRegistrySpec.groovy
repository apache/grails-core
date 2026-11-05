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

import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.JoinType
import spock.lang.Specification

/**
 * Unit tests for AliasRegistry.
 */
class AliasRegistrySpec extends Specification {

    def "test define and check alias"() {
        given:
        def registry = new AliasRegistry()
        def definition = new HibernateAlias("face", "f", JoinType.INNER)

        when:
        registry.define("f", definition)

        then:
        registry.isDefined("f")
        registry.getDefinition("f") == definition
        !registry.hasRealized("f")
    }

    def "test realize and check alias"() {
        given:
        def registry = new AliasRegistry()
        def expression = Mock(Expression)

        when:
        registry.realize("f", expression)

        then:
        registry.hasRealized("f")
        registry.getRealized("f") == expression
    }

    def "test parent delegation for definitions"() {
        given:
        def parent = new AliasRegistry()
        def child = new AliasRegistry(parent)
        def definition = new HibernateAlias("face", "f", JoinType.INNER)

        when:
        parent.define("f", definition)

        then:
        child.isDefined("f")
        child.getDefinition("f") == definition
    }

    def "test parent delegation for realization"() {
        given:
        def parent = new AliasRegistry()
        def child = new AliasRegistry(parent)
        def expression = Mock(Expression)

        when:
        parent.realize("f", expression)

        then:
        child.hasRealized("f")
        child.getRealized("f") == expression
    }

    def "test child override"() {
        given:
        def parent = new AliasRegistry()
        def child = new AliasRegistry(parent)
        def parentExpr = Mock(Expression)
        def childExpr = Mock(Expression)

        when:
        parent.realize("f", parentExpr)
        child.realize("f", childExpr)

        then:
        child.getRealized("f") == childExpr
        parent.getRealized("f") == parentExpr
    }

    def 'a local definition shadows an inherited realized alias until it is realized locally'() {
        given:
        def parent = new AliasRegistry()
        def child = new AliasRegistry(parent)
        def parentExpression = Mock(Expression)
        def childExpression = Mock(Expression)
        parent.realize('f', parentExpression)

        when:
        child.define('f', new HibernateAlias('face', 'f'))

        then:
        child.isDefined('f')
        !child.hasRealized('f')
        child.getRealized('f') == null
        parent.getRealized('f') == parentExpression

        when:
        child.realize('f', childExpression)

        then:
        child.getRealized('f') == childExpression
        parent.getRealized('f') == parentExpression
    }

    def 'association alias selection follows local declaration order, not alias spelling'() {
        given:
        def parent = new AliasRegistry()
        parent.define('outer', new HibernateAlias('likes', 'outer'))
        def registry = new AliasRegistry(parent)
        registry.define('likes', new HibernateAlias('likes', 'likes', JoinType.INNER, false))

        expect:
        registry.getAssociationAlias('likes') == null

        when:
        def first = new HibernateAlias('likes', 'likes')
        registry.define('likes', first)
        registry.define('other', new HibernateAlias('likes', 'other'))

        then:
        registry.getAssociationAlias('likes') == first
        registry.getAssociationAlias('favorite') == null
    }
}
