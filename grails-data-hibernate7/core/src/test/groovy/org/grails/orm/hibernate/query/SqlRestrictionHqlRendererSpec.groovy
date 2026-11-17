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
package org.grails.orm.hibernate.query

import spock.lang.Specification

import org.grails.datastore.mapping.core.exceptions.ConfigurationException
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Association

class SqlRestrictionHqlRendererSpec extends Specification {

    private final SqlRestrictionHqlRenderer renderer = new SqlRestrictionHqlRenderer()

    private PersistentEntity entityWithId(String idName) {
        PersistentProperty id = Stub(PersistentProperty) { getName() >> idName }
        Stub(PersistentEntity) { getIdentity() >> id }
    }

    void "SQL without a placeholder or alias is a call with the literal only"() {
        given:
        StringBuilder where = new StringBuilder()
        List parameters = []

        when:
        int position = renderer.render(new SqlRestriction('1 = 1', []), entityWithId('id'), 'pet', where, 3, parameters)

        then:
        where.toString() == "grails_sql_restriction('1 = 1')"
        position == 3
        parameters.isEmpty()
    }

    void "the alias placeholder adds the identifier of the entity as a column"() {
        given:
        StringBuilder where = new StringBuilder()

        when:
        renderer.render(new SqlRestriction('{alias}.weight > 25', []), entityWithId('id'), 'bwPet', where, 0, [])

        then:
        where.toString() == "grails_sql_restriction('{alias}.weight > 25', bwPet.id)"
    }

    void "each value becomes the next named parameter after the position"() {
        given:
        StringBuilder where = new StringBuilder()
        List parameters = ['earlier']

        when:
        int position = renderer.render(new SqlRestriction('a > ? and b < ?', [15, 'x']), entityWithId('id'), 'pet', where, 4, parameters)

        then:
        where.toString() == "grails_sql_restriction('a > ? and b < ?', :p5, :p6)"
        position == 6
        parameters == ['earlier', 15, 'x']
    }

    void "a quote in the SQL is escaped in the literal"() {
        given:
        StringBuilder where = new StringBuilder()

        when:
        renderer.render(new SqlRestriction("name = 'rex'", []), entityWithId('id'), 'pet', where, 0, [])

        then:
        where.toString() == "grails_sql_restriction('name = ''rex''')"
    }

    void "an identifier that is an association is followed to the column it refers to"() {
        given:
        PersistentProperty inner = Stub(PersistentProperty) { getName() >> 'code' }
        PersistentEntity target = Stub(PersistentEntity) { getIdentity() >> inner }
        Association outer = Stub(Association) {
            getName() >> 'parent'
            getAssociatedEntity() >> target
        }
        PersistentEntity entity = Stub(PersistentEntity) { getIdentity() >> outer }
        StringBuilder where = new StringBuilder()

        when:
        renderer.render(new SqlRestriction('{alias}.x = 1', []), entity, 'child', where, 0, [])

        then:
        where.toString() == "grails_sql_restriction('{alias}.x = 1', child.parent.code)"
    }

    void "the first property of a composite identifier is used when there is no single identifier"() {
        given:
        PersistentProperty first = Stub(PersistentProperty) { getName() >> 'a' }
        PersistentProperty second = Stub(PersistentProperty) { getName() >> 'b' }
        PersistentEntity entity = Stub(PersistentEntity) {
            getIdentity() >> null
            getCompositeIdentity() >> ([first, second] as PersistentProperty[])
        }
        StringBuilder where = new StringBuilder()

        when:
        renderer.render(new SqlRestriction('{alias}.x = 1', []), entity, 'row', where, 0, [])

        then:
        where.toString() == "grails_sql_restriction('{alias}.x = 1', row.a)"
    }

    void "the alias placeholder on an entity without an identifier is refused"() {
        given:
        PersistentEntity entity = Stub(PersistentEntity) {
            getIdentity() >> null
            getCompositeIdentity() >> null
            getJavaClass() >> String
        }

        when:
        renderer.render(new SqlRestriction('{alias}.x = 1', []), entity, 'row', new StringBuilder(), 0, [])

        then:
        ConfigurationException e = thrown()
        e.message.contains('java.lang.String')
    }
}
