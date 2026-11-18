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
package org.grails.datastore.gorm.query.transform

import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.grails.datastore.mapping.query.Query

import spock.lang.Specification

/**
 * When an aggregate function such as {@code avg(...)} is called directly (bare, implicit-this) rather
 * than via the {@code .of()} subquery form, {@code DetachedCriteriaTransformer#addCriteriaCall} requires
 * its single argument to be a plain property reference: a compile error is raised if the argument is an
 * expression (e.g. {@code price + 1}) rather than a variable/constant, and a different compile error is
 * raised if it textually looks like a property reference but does not actually resolve to one on the
 * current class. Separately, the {@code property(...)} pseudo aggregate function (distinct from a real
 * aggregate like {@code avg}/{@code sum}) combined with one of the subquery-mappable comparison operators
 * (eq/gt/lt/ge/le) is rewritten into a {@code *All} subquery criterion (e.g. {@code gtAll}) rather than a
 * plain comparison. All three cases only append/build in-memory {@link grails.gorm.DetachedCriteria}
 * state, so - like {@link WhereQueryOperatorSpec} - they can be verified without a live datastore.
 */
class WhereQueryAggregateSubqueryErrorSpec extends Specification {

    // The domain class name must be unique across the test JVM because
    // AstPropertyResolveUtils caches resolved properties statically by class name
    private static final String SOURCE = '''
import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity

@Entity
class AggSubqueryBook {
    String title
    BigDecimal price
    BigDecimal total
    Integer minStock

    static DetachedCriteria<AggSubqueryBook> propertySubqueryQuery = AggSubqueryBook.where {
        price > property(minStock)
    }
}
'''

    private static Class<?> compile() {
        new GroovyClassLoader().parseClass(SOURCE)
    }

    void "the property() pseudo aggregate combined with a subquery-mappable operator produces an *All subquery criterion"() {
        given:
        Query.GreaterThanAll criterion = compile().propertySubqueryQuery.criteria[0]

        expect:
        criterion.property == 'price'
        criterion.value instanceof grails.gorm.DetachedCriteria

        when:
        grails.gorm.DetachedCriteria subquery = (grails.gorm.DetachedCriteria) criterion.value

        then: 'the subquery carries a plain property projection rather than an aggregate function'
        subquery.projections.size() == 1
        subquery.projections[0].class == Query.PropertyProjection
        ((Query.PropertyProjection) subquery.projections[0]).propertyName == 'minStock'
        subquery.criteria.empty
    }

    void "calling an aggregate function directly with a non-property expression argument fails to compile"() {
        when:
        new GroovyClassLoader().parseClass('''
import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.query.transform.ApplyDetachedCriteriaTransform

@ApplyDetachedCriteriaTransform
@Entity
class AggExpressionArgBook {
    BigDecimal price
    BigDecimal total

    static findInvalid() {
        AggExpressionArgBook.where {
            total > avg(price + 1)
        }
    }
}
''')

        then:
        MultipleCompilationErrorsException e = thrown()
        e.message.contains('Cannot use aggregate function avg on expressions')
    }

    void "calling an aggregate function directly on an unknown property fails to compile"() {
        when:
        new GroovyClassLoader().parseClass('''
import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.query.transform.ApplyDetachedCriteriaTransform

@ApplyDetachedCriteriaTransform
@Entity
class AggUnknownPropertyBook {
    BigDecimal price
    BigDecimal total

    static findInvalid() {
        AggUnknownPropertyBook.where {
            total > avg(nonExistentProperty)
        }
    }
}
''')

        then:
        MultipleCompilationErrorsException e = thrown()
        e.message.contains('Cannot use aggregate function avg on property "nonExistentProperty"')
        e.message.contains('no such property on class')
    }
}
