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
 * {@code !(...)} inside a where-query is rewritten by {@code DetachedCriteriaTransformer#handleNegation}
 * into a {@code this.not { ... }} call, which builds a {@link Query.Negation} junction. Negation only
 * accepts a binary expression as its operand; anything else is a compile-time error.
 */
class WhereQueryNegationSpec extends Specification {

    // The domain class name must be unique across the test JVM because
    // AstPropertyResolveUtils caches resolved properties statically by class name
    private static final String SOURCE = '''
import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity

@Entity
class NegQueryBook {
    String title
    BigDecimal price

    static DetachedCriteria<NegQueryBook> singleNegation = NegQueryBook.where {
        !(title == "Excluded")
    }

    static DetachedCriteria<NegQueryBook> negationOfDisjunction = NegQueryBook.where {
        !(title == "Foo" || title == "Bar")
    }

    static DetachedCriteria<NegQueryBook> negationCombinedWithConjunction = NegQueryBook.where {
        title == "Foo" && !(price > 10)
    }
}
'''

    private static Class<?> compile() {
        new GroovyClassLoader().parseClass(SOURCE)
    }

    void "negating a single criterion produces a Negation junction wrapping it"() {
        given:
        Query.Negation negation = compile().singleNegation.criteria[0]

        expect:
        negation.criteria.size() == 1
        negation.criteria[0] instanceof Query.Equals
        ((Query.Equals) negation.criteria[0]).property == 'title'
        ((Query.Equals) negation.criteria[0]).value == 'Excluded'
    }

    void "negating a disjunction produces a Negation junction wrapping a Disjunction"() {
        given:
        Query.Negation negation = compile().negationOfDisjunction.criteria[0]

        expect:
        negation.criteria.size() == 1
        negation.criteria[0] instanceof Query.Disjunction
        ((Query.Disjunction) negation.criteria[0]).criteria.size() == 2
    }

    void "negation combined with a non-negated criterion via && produces a Conjunction containing a Negation"() {
        given:
        Query.Conjunction conjunction = compile().negationCombinedWithConjunction.criteria[0]

        expect:
        conjunction.criteria.size() == 2
        conjunction.criteria[0] instanceof Query.Equals
        conjunction.criteria[1] instanceof Query.Negation

        and:
        Query.Negation negation = conjunction.criteria[1]
        negation.criteria[0] instanceof Query.GreaterThan
        ((Query.GreaterThan) negation.criteria[0]).property == 'price'
    }

    void "negating a non-binary expression fails to compile"() {
        when:
        new GroovyClassLoader().parseClass('''
import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.query.transform.ApplyDetachedCriteriaTransform

@ApplyDetachedCriteriaTransform
@Entity
class InvalidNegationBook {
    String title

    static findInvalid() {
        InvalidNegationBook.where {
            !(title)
        }
    }
}
''')

        then:
        MultipleCompilationErrorsException e = thrown()
        e.message.contains('You can only negate a binary expressions in queries')
    }
}
