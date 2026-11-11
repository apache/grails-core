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

import spock.lang.Specification

import org.grails.datastore.gorm.query.criteria.DetachedAssociationCriteria
import org.grails.datastore.mapping.query.Query

class CriteriaVisitorSpec extends Specification {

    private static class Recorder implements CriteriaVisitor {
        List<String> events = []

        @Override
        void alias(HibernateAlias alias) {
            events << "alias ${alias.alias()}".toString()
        }

        @Override
        void block(DetachedAssociationCriteria<?> block) {
            events << 'block'
        }

        @Override
        void associationQuery(HibernateAssociationQuery query) {
            events << 'associationQuery'
        }

        @Override
        void property(String property) {
            events << "property ${property}".toString()
        }

        @Override
        void enter(Query.Junction junction) {
            events << "enter ${junction.class.simpleName}".toString()
        }

        @Override
        void leave(Query.Junction junction) {
            events << "leave ${junction.class.simpleName}".toString()
        }
    }

    private List<String> walk(List<Query.Criterion> criteria) {
        def recorder = new Recorder()
        CriteriaVisitor.walk(criteria, recorder)
        recorder.events
    }

    def 'a null list has nothing to visit'() {
        expect:
        walk(null) == []
    }

    def 'criteria are visited in the order they were added'() {
        expect:
        walk([
            new Query.Equals('a.name', 'x'),
            new HibernateAlias('team', 't'),
            Mock(DetachedAssociationCriteria),
            Mock(HibernateAssociationQuery),
            new Query.IsNull('b.tag')
        ]) == ['property a.name', 'alias t', 'block', 'associationQuery', 'property b.tag']
    }

    def 'a comparison of two properties visits both names'() {
        expect:
        walk([new Query.EqualsProperty('a.label', 'b.label')]) == ['property a.label', 'property b.label']
    }

    def 'every criterion that names a property is visited by that name'() {
        expect:
        walk([criterion]) == ['property team.likes.label']

        where:
        criterion << [
            new Query.IsNull('team.likes.label'),
            new Query.IsNotNull('team.likes.label'),
            new Query.IsEmpty('team.likes.label'),
            new Query.IsNotEmpty('team.likes.label'),
            new Query.Equals('team.likes.label', 'x'),
            new Query.NotEquals('team.likes.label', 'x'),
            new Query.GreaterThan('team.likes.label', 'x'),
            new Query.LessThanEquals('team.likes.label', 'x'),
            new Query.Like('team.likes.label', 'x%'),
            new Query.Between('team.likes.label', 'a', 'b'),
            new Query.In('team.likes.label', ['x']),
            new Query.SizeEquals('team.likes.label', 1),
        ]
    }

    def 'junctions are walked into and their criteria are visited in place'() {
        given:
        def nested = new Query.Disjunction([new Query.Equals('a.name', 'x'), new Query.Negation().add(new Query.IsNull('b.tag'))])

        expect:
        walk([new Query.Conjunction([nested, new Query.Equals('c.name', 'y')])]) ==
            ['enter Conjunction', 'enter Disjunction', 'property a.name', 'enter Negation', 'property b.tag',
             'leave Negation', 'leave Disjunction', 'property c.name', 'leave Conjunction']
    }

    def 'a junction is left even when a visitor throws while its criteria are visited'() {
        given:
        def events = []
        def visitor = new CriteriaVisitor() {
            @Override
            void leave(Query.Junction junction) {
                events << 'leave'
            }

            @Override
            void property(String property) {
                throw new IllegalStateException('stop')
            }
        }

        when:
        CriteriaVisitor.walk([new Query.Disjunction([new Query.Equals('a', 'x')])], visitor)

        then:
        thrown(IllegalStateException)
        events == ['leave']
    }

    def 'a block is reported and its criteria are left to the visitor of the block'() {
        given:
        def block = Mock(DetachedAssociationCriteria)

        when:
        def events = walk([block])

        then:
        events == ['block']
        0 * block.getCriteria()
    }

    def 'criteria that have nothing to say about the paths of the query are not visited'() {
        expect:
        walk([new Query.Exists(Mock(org.grails.datastore.mapping.query.api.QueryableCriteria)),
              new Query.NotExists(Mock(org.grails.datastore.mapping.query.api.QueryableCriteria)),
              new SqlRestriction('a = 1', [])]) == []
    }
}
