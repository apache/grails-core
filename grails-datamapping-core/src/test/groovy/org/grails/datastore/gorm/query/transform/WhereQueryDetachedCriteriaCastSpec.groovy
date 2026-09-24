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
 * A closure cast to {@code DetachedCriteria<SomeDomain>} - for example
 * {@code def query = { name == value } as DetachedCriteria<Book>} - is rewritten by
 * {@code DetachedCriteriaTransformer#handleDetachedCriteriaCast}. Unlike the static-field form, the cast
 * form is applied both to instance fields ({@code visitField}) and to local variable declarations
 * ({@code visitDeclarationExpression}), and in both cases the transform replaces the initializer with the
 * transformed closure itself (the cast is dropped once the closure body has been rewritten). Because the
 * resulting closure only builds flat, non-association criteria in these specs, it can be executed directly
 * against a plain {@link grails.gorm.DetachedCriteria} via the public {@code build(Closure)} API without
 * any live datastore.
 */
class WhereQueryDetachedCriteriaCastSpec extends Specification {

    // The domain class names must be unique across the test JVM because
    // AstPropertyResolveUtils caches resolved properties statically by class name
    private static final String FIELD_CAST_SOURCE = '''
import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity

class CastFieldQueryHolder {
    Closure priceQuery = { price > 100 } as DetachedCriteria<CastFieldBook>
}

@Entity
class CastFieldBook {
    String title
    BigDecimal price
}
'''

    private static final String LOCAL_VAR_CAST_SOURCE = '''
import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity

class CastLocalVarQueryHolder {
    Closure makeQuery() {
        def query = {
            if (true) {
                title == "Local"
            }
        } as DetachedCriteria<CastLocalVarBook>
        return query
    }
}

@Entity
class CastLocalVarBook {
    String title
}
'''

    void "a closure cast on an instance field is transformed and can be built into real criteria"() {
        given:
        GroovyClassLoader gcl = new GroovyClassLoader()
        gcl.parseClass(FIELD_CAST_SOURCE)
        def holder = gcl.loadClass('CastFieldQueryHolder').getDeclaredConstructor().newInstance()
        Closure transformedClosure = holder.priceQuery

        when:
        def criteria = new grails.gorm.DetachedCriteria(gcl.loadClass('CastFieldBook')).build(transformedClosure)

        then:
        criteria.criteria.size() == 1
        criteria.criteria[0] instanceof Query.GreaterThan
        ((Query.GreaterThan) criteria.criteria[0]).property == 'price'
        ((Query.GreaterThan) criteria.criteria[0]).value == 100
    }

    void "a closure cast on a local variable declaration is transformed, including a nested if statement in its body"() {
        given:
        GroovyClassLoader gcl = new GroovyClassLoader()
        gcl.parseClass(LOCAL_VAR_CAST_SOURCE)
        def holder = gcl.loadClass('CastLocalVarQueryHolder').getDeclaredConstructor().newInstance()
        Closure transformedClosure = holder.makeQuery()

        when:
        def criteria = new grails.gorm.DetachedCriteria(gcl.loadClass('CastLocalVarBook')).build(transformedClosure)

        then:
        criteria.criteria.size() == 1
        criteria.criteria[0] instanceof Query.Equals
        ((Query.Equals) criteria.criteria[0]).property == 'title'
        ((Query.Equals) criteria.criteria[0]).value == 'Local'
    }

    void "a closure cast referencing an unknown property fails to compile"() {
        when:
        new GroovyClassLoader().parseClass('''
import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity

class CastInvalidPropertyQueryHolder {
    Closure query = { unknownProperty == "x" } as DetachedCriteria<CastInvalidPropertyBook>
}

@Entity
class CastInvalidPropertyBook {
    String title
}
''')

        then:
        MultipleCompilationErrorsException e = thrown()
        e.message.contains('Cannot query on property "unknownProperty"')
    }
}
