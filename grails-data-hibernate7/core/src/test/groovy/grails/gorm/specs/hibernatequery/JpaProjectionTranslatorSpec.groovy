/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package grails.gorm.specs.hibernatequery

import grails.gorm.specs.HibernateGormDatastoreSpec
import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.Root
import org.grails.datastore.mapping.query.Query
import org.grails.orm.hibernate.query.JpaProjectionTranslator
import org.grails.orm.hibernate.query.JpaQueryContext
import org.hibernate.query.criteria.JpaCriteriaQuery
import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.GormEntity

class JpaProjectionTranslatorSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.addAllDomainClasses([JpaProjectionTranslatorSpecPerson])
    }

    def 'translate PropertyProjection'() {
        given:
        JpaCriteriaQuery cq = criteriaBuilder.createQuery(JpaProjectionTranslatorSpecPerson)
        Root root = cq.from(JpaProjectionTranslatorSpecPerson)
        JpaQueryContext context = new JpaQueryContext()
        context.setRoot(root)
        JpaProjectionTranslator translator = new JpaProjectionTranslator(criteriaBuilder, context)

        when:
        Expression result = translator.translate(new Query.PropertyProjection('firstName'))

        then:
        result != null
        result.getJavaType() == String
    }

    def 'translate aliased PropertyProjection'() {
        given:
        JpaCriteriaQuery cq = criteriaBuilder.createQuery(JpaProjectionTranslatorSpecPerson)
        Root root = cq.from(JpaProjectionTranslatorSpecPerson)
        JpaQueryContext context = new JpaQueryContext()
        context.setRoot(root)
        JpaProjectionTranslator translator = new JpaProjectionTranslator(criteriaBuilder, context)

        when:
        Expression result = translator.translate(new Query.PropertyProjection('cnt:firstName'))

        then:
        result != null
        result.getAlias() == 'cnt'
        context.hasAlias('cnt')
    }

    def 'translate CountProjection'() {
        given:
        JpaCriteriaQuery cq = criteriaBuilder.createQuery(Long)
        Root root = cq.from(JpaProjectionTranslatorSpecPerson)
        JpaQueryContext context = new JpaQueryContext()
        context.setRoot(root)
        JpaProjectionTranslator translator = new JpaProjectionTranslator(criteriaBuilder, context)

        when:
        Expression result = translator.translate(new org.grails.orm.hibernate.query.Hibernate7CountProjection('firstName'))

        then:
        result != null
        result.getJavaType() == Long
    }

    def 'translate CountDistinctProjection'() {
        given:
        JpaCriteriaQuery cq = criteriaBuilder.createQuery(Long)
        Root root = cq.from(JpaProjectionTranslatorSpecPerson)
        JpaQueryContext context = new JpaQueryContext()
        context.setRoot(root)
        JpaProjectionTranslator translator = new JpaProjectionTranslator(criteriaBuilder, context)

        when:
        Expression result = translator.translate(new Query.CountDistinctProjection('firstName'))

        then:
        result != null
        result.getJavaType() == Long
    }

    def 'translate MaxProjection'() {
        given:
        JpaCriteriaQuery cq = criteriaBuilder.createQuery(Integer)
        Root root = cq.from(JpaProjectionTranslatorSpecPerson)
        JpaQueryContext context = new JpaQueryContext()
        context.setRoot(root)
        JpaProjectionTranslator translator = new JpaProjectionTranslator(criteriaBuilder, context)

        when:
        Expression result = translator.translate(new Query.MaxProjection('age'))

        then:
        result != null
        result.getJavaType() == Integer
    }
}

@Entity
class JpaProjectionTranslatorSpecPerson implements GormEntity<JpaProjectionTranslatorSpecPerson> {

    Long id
    String firstName
    Integer age
}
