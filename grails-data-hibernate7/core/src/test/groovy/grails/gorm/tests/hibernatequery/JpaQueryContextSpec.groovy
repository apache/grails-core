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
package grails.gorm.tests.hibernatequery

import grails.gorm.DetachedCriteria
import grails.gorm.tests.HibernateGormDatastoreSpec
import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.From
import jakarta.persistence.criteria.Path
import org.grails.orm.hibernate.query.JpaQueryContext
import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.GormEntity
import org.hibernate.query.criteria.JpaExpression

class JpaQueryContextSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.addAllDomainClasses([JpaQueryContextSpecPerson])
    }

    def "getRoot returns the assigned root"() {
        given:
        From root = Mock(From)
        JpaQueryContext context = new JpaQueryContext()
        context.setRoot(root)

        expect:
        context.getRoot() == root
    }

    def "registerAlias and hasAlias"() {
        given:
        JpaQueryContext context = new JpaQueryContext()
        Expression expr = Mock(Expression)

        when:
        context.registerAlias("myAlias", expr)

        then:
        context.hasAlias("myAlias")
        context.getAliasedExpression("myAlias") == expr
    }

    def "registerAliasFromPath parses separator"() {
        given:
        JpaQueryContext context = new JpaQueryContext()

        when:
        context.registerAliasFromPath("cnt:firstName")

        then:
        context.hasAlias("cnt")
        context.getAliasedExpression("cnt") == null // Registered as placeholder
    }

    def "getFullyQualifiedExpression handles root"() {
        given:
        From root = Mock(From)
        JpaQueryContext context = new JpaQueryContext()
        context.setRoot(root)

        expect:
        context.getFullyQualifiedExpression("root") == root
    }

    def "getFullyQualifiedExpression handles root prefix and alias registration"() {
        given:
        Path firstNamePath = Mock(Path)
        From root = Mock(From) {
            get("firstName") >> firstNamePath
        }
        JpaQueryContext context = new JpaQueryContext()
        context.setRoot(root)

        when:
        Expression result = context.getFullyQualifiedExpression("root.firstName")

        then:
        result == firstNamePath
    }

    def "getFullyQualifiedExpression handles ALIAS_SEPARATOR"() {
        given:
        // Use a mock that implements both JpaExpression and Path since getFullyQualifiedPath expects Path
        JpaExpression firstNameExpr = Mock(JpaExpression, additionalInterfaces: [Path])
        
        From root = Mock(From) {
            get("firstName") >> (Path)firstNameExpr
        }
        JpaQueryContext context = new JpaQueryContext()
        context.setRoot(root)

        when:
        Expression result = context.getFullyQualifiedExpression("cnt:firstName")

        then:
        result == firstNameExpr
        1 * firstNameExpr.alias("cnt")
        context.hasAlias("cnt")
        context.getAliasedExpression("cnt") == firstNameExpr
    }

    def "getFullyQualifiedPath handles nested paths"() {
        given:
        Path cityPath = Mock(Path)
        Path addressPath = Mock(Path) {
            get("city") >> cityPath
        }
        From root = Mock(From) {
            get("address") >> addressPath
        }
        JpaQueryContext context = new JpaQueryContext()
        context.setRoot(root)

        expect:
        context.getFullyQualifiedPath("address.city") == cityPath
    }

    def "getFullyQualifiedPath returns null for non-path alias"() {
        given:
        Expression countExpr = Mock(Expression)
        JpaQueryContext context = new JpaQueryContext()
        context.registerAlias("cnt", countExpr)

        expect:
        context.getFullyQualifiedPath("cnt") == null
    }
}

@Entity
class JpaQueryContextSpecPerson implements GormEntity<JpaQueryContextSpecPerson> {
    Long id
    String firstName
}
