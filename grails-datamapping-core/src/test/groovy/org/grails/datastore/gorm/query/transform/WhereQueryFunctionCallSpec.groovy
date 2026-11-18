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
import org.grails.datastore.mapping.query.criteria.FunctionCallingCriterion

import spock.lang.Specification

/**
 * SQL functions such as {@code year(...)}, {@code lower(...)} etc. (see
 * {@code DetachedCriteriaTransformer#SUPPORTED_FUNCTIONS}) are only recognised on the left-hand side of
 * a comparison, where {@code DetachedCriteriaTransformer#handleFunctionCall} rewrites them into a
 * {@link FunctionCallingCriterion}. A function call on a direct property never needs a live datastore, so
 * it can be built and inspected in memory; a function call through an association property additionally
 * exercises the association-walking branch of {@code handleAssociationQueryViaPropertyExpression}, which -
 * like all association criteria - requires a GORM-enhanced entity to execute, so that variant is only
 * verified structurally (the source compiles and a nested association closure is generated).
 */
class WhereQueryFunctionCallSpec extends Specification {

    // The domain class name must be unique across the test JVM because
    // AstPropertyResolveUtils caches resolved properties statically by class name
    private static final String DIRECT_SOURCE = '''
import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity

@Entity
class FuncQueryBook {
    String title
    Date published

    static DetachedCriteria<FuncQueryBook> byPublishedYear = FuncQueryBook.where {
        year(published) == 2020
    }

    static DetachedCriteria<FuncQueryBook> byLowerTitle = FuncQueryBook.where {
        lower(title) == "effective java"
    }
}
'''

    private static final String ASSOCIATION_SOURCE = '''
import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity

class FuncQueryAssocBookQueryService {
    protected DetachedCriteria<FuncQueryAssocBook> findByAuthorBirthYear(int yr) {
        FuncQueryAssocBook.where {
            year(author.birthDate) == yr
        }
    }
}

@Entity
class FuncQueryAssocBook {
    String title
    FuncQueryAssocAuthor author
}

@Entity
class FuncQueryAssocAuthor {
    Date birthDate
}
'''

    private static Class<?> compileDirect() {
        new GroovyClassLoader().parseClass(DIRECT_SOURCE)
    }

    void "a function call on a direct property produces a FunctionCallingCriterion"() {
        given:
        FunctionCallingCriterion criterion = compileDirect().byPublishedYear.criteria[0]

        expect:
        criterion.functionName == 'year'
        criterion.propertyCriterion instanceof Query.Equals
        criterion.propertyCriterion.property == 'published'
        criterion.propertyCriterion.value == 2020
    }

    void "a different supported function (lower) also produces a FunctionCallingCriterion"() {
        given:
        FunctionCallingCriterion criterion = compileDirect().byLowerTitle.criteria[0]

        expect:
        criterion.functionName == 'lower'
        criterion.propertyCriterion.property == 'title'
        criterion.propertyCriterion.value == 'effective java'
    }

    void "a function call through an association property compiles and generates a nested association closure"() {
        given:
        GroovyClassLoader gcl = new GroovyClassLoader()

        when:
        gcl.parseClass(ASSOCIATION_SOURCE)

        then:
        noExceptionThrown()

        and: 'a nested closure was synthesized for the association block driven by the function call'
        List<Class<?>> queryClosures = gcl.loadedClasses.findAll {
            it.name.contains('_findByAuthorBirthYear_')
        }
        queryClosures.size() > 1
    }

    void "a function call used on the right-hand side of a comparison fails to compile"() {
        when:
        new GroovyClassLoader().parseClass('''
import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.query.transform.ApplyDetachedCriteriaTransform

@ApplyDetachedCriteriaTransform
@Entity
class RhsFunctionCallBook {
    String title
    Date published

    static findInvalid() {
        RhsFunctionCallBook.where {
            title == year(published)
        }
    }
}
''')

        then:
        MultipleCompilationErrorsException e = thrown()
        e.message.contains('Function calls can currently only be used on the left-hand side of expressions')
    }
}
