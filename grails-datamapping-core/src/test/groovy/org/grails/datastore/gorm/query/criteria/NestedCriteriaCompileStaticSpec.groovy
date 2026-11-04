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
package org.grails.datastore.gorm.query.criteria

import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity
import groovy.transform.CompileStatic
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.mapping.query.api.QueryableCriteria
import spock.lang.Specification

/**
 * Regression tests for criteria closures under {@code @CompileStatic}.
 *
 * With {@code @CompileStatic} the Groovy static compiler used to bind the calls of a nested
 * criteria closure to the delegate of the enclosing closure (the outer criteria), so subquery
 * restrictions were added to the outer query and the subquery was left empty. Projection blocks
 * silently dropped their projections for the same reason. These specs cover the affected forms
 * (explicit criteria methods, the where-query syntax, junctions, nested {@code build}, the
 * closure subquery helpers and {@code projections}), the two documented behaviour changes
 * (closure forwarding and name resolution) and static-vs-dynamic parity of the built criteria.
 */
class NestedCriteriaCompileStaticSpec extends Specification {

    void 'exists with an inline where subquery keeps the restriction on the inner criteria'() {
        given:
        NestedCriteriaFixture fixture = new NestedCriteriaFixture()

        when:
        fixture.existsWithEq()

        then: 'the outer criteria only holds the exists criterion'
        fixture.outer.criteria.size() == 1
        fixture.outer.criteria[0] instanceof Query.Exists

        and: 'the subquery restriction stays on the inner criteria'
        fixture.inner.criteria.size() == 1
        Query.Equals equals = (Query.Equals) fixture.inner.criteria[0]
        equals.property == 'title'
        equals.value == 'X'
    }

    void 'notExists with an inline where subquery keeps the restriction on the inner criteria'() {
        given:
        NestedCriteriaFixture fixture = new NestedCriteriaFixture()

        when:
        fixture.notExistsWithEq()

        then:
        fixture.outer.criteria.size() == 1
        fixture.outer.criteria[0] instanceof Query.NotExists

        and:
        fixture.inner.criteria.size() == 1
        Query.Equals equals = (Query.Equals) fixture.inner.criteria[0]
        equals.property == 'title'
    }

    void 'eqProperty inside the nested where stays on the inner criteria'() {
        given:
        NestedCriteriaFixture fixture = new NestedCriteriaFixture()

        when:
        fixture.existsWithEqProperty()

        then:
        fixture.outer.criteria.size() == 1
        fixture.outer.criteria[0] instanceof Query.Exists
        !fixture.outer.criteria.any { it instanceof Query.EqualsProperty }

        and:
        fixture.inner.criteria.size() == 1
        Query.EqualsProperty comparison = (Query.EqualsProperty) fixture.inner.criteria[0]
        comparison.property == 'title'
        comparison.otherProperty == 'subtitle'
    }

    void 'the where query syntax with typed variables keeps the restriction on the inner criteria'() {
        given:
        NestedCriteriaFixture fixture = new NestedCriteriaFixture()

        when:
        fixture.whereQuerySyntax()

        then:
        fixture.outer.criteria.size() == 1
        fixture.outer.criteria[0] instanceof Query.Exists
        Query.Exists exists = (Query.Exists) fixture.outer.criteria[0]
        exists.subquery.criteria.size() == 1
        ((Query.Equals) exists.subquery.criteria[0]).property == 'title'
    }

    void 'a nested where inside an or junction keeps the restriction on the inner criteria'() {
        given:
        NestedCriteriaFixture fixture = new NestedCriteriaFixture()

        when:
        fixture.existsInsideOrJunction()

        then: 'the junction holds only the exists criterion'
        fixture.outer.criteria.size() == 1
        Query.Junction junction = (Query.Junction) fixture.outer.criteria[0]
        junction.criteria.size() == 1
        junction.criteria[0] instanceof Query.Exists

        and: 'the subquery restriction stays on the inner criteria'
        Query.Exists exists = (Query.Exists) junction.criteria[0]
        exists.subquery.criteria.size() == 1
        ((Query.Equals) exists.subquery.criteria[0]).property == 'title'
    }

    void 'a nested build keeps the restriction on the inner criteria'() {
        given:
        NestedCriteriaFixture fixture = new NestedCriteriaFixture()

        when:
        fixture.existsWithBuild()

        then:
        fixture.outer.criteria.size() == 1
        fixture.outer.criteria[0] instanceof Query.Exists
        Query.Exists exists = (Query.Exists) fixture.outer.criteria[0]
        exists.subquery.criteria.size() == 1
        ((Query.Equals) exists.subquery.criteria[0]).property == 'title'
    }

    void 'a nested inList closure subquery keeps its restrictions and projections on the inner criteria'() {
        given:
        NestedCriteriaFixture fixture = new NestedCriteriaFixture()

        when:
        fixture.inListClosureSubquery()

        then:
        fixture.outer.criteria.size() == 1
        Query.In in = (Query.In) fixture.outer.criteria[0]
        in.property == 'id'

        and:
        in.subquery.criteria.size() == 1
        Query.Equals equals = (Query.Equals) in.subquery.criteria[0]
        equals.property == 'name'
        equals.value == 'X'

        and: 'the projection block stays on the inner criteria'
        in.subquery.projections*.class == [Query.IdProjection]
    }

    void 'a nested gtAll closure subquery keeps its restrictions and projections on the inner criteria'() {
        given:
        NestedCriteriaFixture fixture = new NestedCriteriaFixture()

        when:
        fixture.gtAllClosureSubquery()

        then:
        fixture.outer.criteria.size() == 1
        Query.SubqueryCriterion subqueryCriterion = (Query.SubqueryCriterion) fixture.outer.criteria[0]
        subqueryCriterion.property == 'id'

        and:
        subqueryCriterion.value.criteria.size() == 1
        ((Query.Equals) subqueryCriterion.value.criteria[0]).property == 'name'

        and:
        subqueryCriterion.value.projections*.class == [Query.IdProjection]
    }

    void 'a projections block on a criteria builds the projection under @CompileStatic'() {
        given:
        NestedCriteriaFixture fixture = new NestedCriteriaFixture()

        when:
        fixture.projectionsProperty()

        then:
        fixture.bookQuery.criteria.isEmpty()
        fixture.bookQuery.projections*.class == [Query.PropertyProjection]
        ((Query.PropertyProjection) fixture.bookQuery.projections[0]).propertyName == 'title'
    }

    void 'count inside a projections block binds to the projection instead of running a query'() {
        given:
        NestedCriteriaFixture fixture = new NestedCriteriaFixture()

        when:
        fixture.projectionsCount()

        then:
        fixture.bookQuery.projections*.class == [Query.CountProjection]
    }

    void 'a hoisted subquery keeps working as a control'() {
        given:
        NestedCriteriaFixture fixture = new NestedCriteriaFixture()

        when:
        fixture.hoistedControl()

        then:
        fixture.outer.criteria.size() == 1
        fixture.outer.criteria[0] instanceof Query.Exists

        and:
        fixture.inner.criteria.size() == 1
        fixture.inner.criteria[0] instanceof Query.Equals
    }

    void 'forwarding a plain Closure parameter to where does not compile'() {
        when:
        compile(FORWARDING_PLAIN_SOURCE)

        then:
        MultipleCompilationErrorsException e = thrown()
        e.message.contains('Closure parameter with resolve strategy OWNER_FIRST passed to method with resolve strategy DELEGATE_FIRST')
    }

    void 'declaring DELEGATE_FIRST on the parameter compiles and builds the right criteria'() {
        when:
        DetachedCriteria criteria = (DetachedCriteria) compileAndRun(FORWARDING_ANNOTATED_SOURCE)

        then:
        criteria.criteria.size() == 1
        Query.Equals equals = (Query.Equals) criteria.criteria[0]
        equals.property == 'title'
        equals.value == 'X'
    }

    void 'casting the argument compiles and builds the right criteria'() {
        when:
        DetachedCriteria criteria = (DetachedCriteria) compileAndRun(FORWARDING_CAST_SOURCE)

        then:
        criteria.criteria.size() == 1
        Query.Equals equals = (Query.Equals) criteria.criteria[0]
        equals.property == 'title'
        equals.value == 'X'
    }

    void 'name resolution inside a criteria closure prefers the criteria delegate'() {
        when:
        DetachedCriteria criteria = (DetachedCriteria) compileAndRun(NAME_RESOLUTION_SOURCE)

        then: 'the class getOrders() value is not used; the criteria order list is'
        criteria.criteria.size() == 1
        Query.In in = (Query.In) criteria.criteria[0]
        in.property == 'name'
        in.values.isEmpty()
    }

    void 'criteria closures build the same criteria with and without @CompileStatic'() {
        given:
        Map<String, Map> forms = parityResults()

        expect: 'every form builds identical criteria and projections under both compilers'
        def mismatches = forms.findAll { String name, Map pair -> describe(pair.static) != describe(pair.dynamic) }
        mismatches.keySet().empty

        and: 'projections survive in the projection forms'
        forms.projectionsProperty.static.projections*.class == [Query.PropertyProjection]
        forms.projectionsCount.static.projections*.class == [Query.CountProjection]
        forms.projectionsAll.static.projections*.class == [
                Query.IdProjection,
                Query.PropertyProjection,
                Query.AvgProjection,
                Query.SumProjection,
                Query.MinProjection,
                Query.MaxProjection,
                Query.DistinctProjection
        ]

        and: 'subquery projections survive in the closure subquery forms'
        ((Query.In) forms.inListClosure.static.criteria[0]).subquery.projections*.class == [Query.IdProjection]
        ((Query.SubqueryCriterion) forms.gtAllClosure.static.criteria[0]).value.projections*.class == [Query.IdProjection]
    }

    static Class compile(String source) {
        new GroovyClassLoader(NestedCriteriaCompileStaticSpec.classLoader).parseClass(source)
    }

    static Object compileAndRun(String source) {
        compile(source).getMethod('run').invoke(null)
    }

    static Map<String, Map> parityResults() {
        String prefix = 'Parity' + System.identityHashCode(PARITY_BODIES)
        String bodies = PARITY_BODIES.replace('PARITY', prefix)
        GroovyClassLoader gcl = new GroovyClassLoader(NestedCriteriaCompileStaticSpec.classLoader)
        gcl.parseClass(PARITY_SOURCE.replace('PARITY', prefix).replace('CLASS', 'Static').replace('COMPILED', '@CompileStatic').replace('BODIES', bodies))
        gcl.parseClass(PARITY_SOURCE.replace('PARITY', prefix).replace('CLASS', 'Dynamic').replace('COMPILED', '').replace('BODIES', bodies))
        Class staticHolder = gcl.loadClass(prefix + 'Static')
        Class dynamicHolder = gcl.loadClass(prefix + 'Dynamic')
        Map<String, Map> results = [:]
        FORMS.each { String form ->
            results[form] = [static: staticHolder.getMethod(form).invoke(null), dynamic: dynamicHolder.getMethod(form).invoke(null)]
        }
        results
    }

    static Object describe(Object element) {
        if (element instanceof Query.Exists || element instanceof Query.NotExists) {
            return [element.class.simpleName, describeCriteria((QueryableCriteria) element.subquery)]
        }
        if (element instanceof Query.SubqueryCriterion) {
            return [element.class.simpleName, element.property, describeCriteria(element.value)]
        }
        if (element instanceof Query.In) {
            return [element.class.simpleName, element.property,
                    element.subquery != null ? describeCriteria(element.subquery) : element.values as List]
        }
        if (element instanceof Query.PropertyComparisonCriterion) {
            return [element.class.simpleName, element.property, element.otherProperty]
        }
        if (element instanceof Query.PropertyCriterion) {
            def value = element.value
            return [element.class.simpleName, element.property,
                    value instanceof QueryableCriteria ? describeCriteria((QueryableCriteria) value) : String.valueOf(value)]
        }
        if (element instanceof Query.Junction) {
            return [element.class.simpleName, element.criteria.collect { describe(it) }]
        }
        if (element instanceof Query.PropertyProjection) {
            return [element.class.simpleName, element.propertyName]
        }
        return [element.class.simpleName]
    }

    static List describeCriteria(QueryableCriteria criteria) {
        [criteria.criteria.collect { describe(it) }, criteria.projections.collect { describe(it) }]
    }

    private static final List<String> FORMS = [
            'nestedExists', 'existsEq', 'eqProperty', 'whereQuerySyntax', 'orJunction', 'buildNested',
            'inListClosure', 'gtAllClosure', 'projectionsProperty', 'projectionsCount', 'projectionsAll'
    ]

    private static final String FORWARDING_PLAIN_SOURCE = '''
import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity
import groovy.transform.CompileStatic

@CompileStatic
class ForwardingPlainHolder {
    static DetachedCriteria<ForwardingPlainBook> withExtra(Closure extra) {
        new DetachedCriteria<ForwardingPlainBook>(ForwardingPlainBook).where(extra)
    }
}

@Entity
class ForwardingPlainBook {
    String title
}
'''

    private static final String FORWARDING_ANNOTATED_SOURCE = '''
import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity
import groovy.transform.CompileStatic

@CompileStatic
class ForwardingAnnotatedHolder {
    static DetachedCriteria<ForwardingAnnotatedBook> withExtra(
            @DelegatesTo(value = DetachedCriteria, strategy = Closure.DELEGATE_FIRST) Closure extra) {
        new DetachedCriteria<ForwardingAnnotatedBook>(ForwardingAnnotatedBook).where(extra)
    }

    static run() {
        withExtra { eq 'title', 'X' }
    }
}

@Entity
class ForwardingAnnotatedBook {
    String title
}
'''

    private static final String FORWARDING_CAST_SOURCE = '''
import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic

@CompileStatic
class ForwardingCastHolder {
    static DetachedCriteria<ForwardingCastBook> withExtra(Closure extra) {
        new DetachedCriteria<ForwardingCastBook>(ForwardingCastBook).where((Closure) extra)
    }

    @CompileDynamic
    static run() {
        withExtra { eq 'title', 'X' }
    }
}

@Entity
class ForwardingCastBook {
    String title
}
'''

    private static final String NAME_RESOLUTION_SOURCE = '''
import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity
import groovy.transform.CompileStatic

@CompileStatic
class NameResolutionHolder {
    List<String> getOrders() { ['from-class'] }

    static run() {
        new NameResolutionHolder().query()
    }

    DetachedCriteria<NameResolutionBook> query() {
        new DetachedCriteria<NameResolutionBook>(NameResolutionBook).where { inList 'name', orders }
    }
}

@Entity
class NameResolutionBook {
    String name
}
'''

    private static final String PARITY_SOURCE = '''
import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity
import groovy.transform.CompileStatic

@Entity
class PARITYBook {
    String title
    String subtitle
    Integer pages
}

@Entity
class PARITYAuthor {
    String name
}

COMPILED
class PARITYCLASS {
BODIES
}
'''

    private static final String PARITY_BODIES = '''
    static DetachedCriteria<PARITYAuthor> nestedExists() {
        DetachedCriteria<PARITYAuthor> authors = new DetachedCriteria<PARITYAuthor>(PARITYAuthor)
        DetachedCriteria<PARITYBook> books = new DetachedCriteria<PARITYBook>(PARITYBook)
        authors.where {
            exists books.where { eq 'title', 'X' }.id()
        }
    }

    static DetachedCriteria<PARITYAuthor> existsEq() {
        DetachedCriteria<PARITYBook> books = new DetachedCriteria<PARITYBook>(PARITYBook)
        new DetachedCriteria<PARITYAuthor>(PARITYAuthor).where {
            exists books.where { eq 'title', 'X' }
        }
    }

    static DetachedCriteria<PARITYAuthor> eqProperty() {
        DetachedCriteria<PARITYBook> books = new DetachedCriteria<PARITYBook>(PARITYBook)
        new DetachedCriteria<PARITYAuthor>(PARITYAuthor).where {
            exists books.where { eqProperty 'title', 'subtitle' }.id()
        }
    }

    static DetachedCriteria<PARITYAuthor> whereQuerySyntax() {
        DetachedCriteria<PARITYAuthor> authors = new DetachedCriteria<PARITYAuthor>(PARITYAuthor)
        DetachedCriteria<PARITYBook> books = new DetachedCriteria<PARITYBook>(PARITYBook)
        authors.where {
            exists books.where { title == 'X' }.id()
        }
    }

    static DetachedCriteria<PARITYAuthor> orJunction() {
        DetachedCriteria<PARITYBook> books = new DetachedCriteria<PARITYBook>(PARITYBook)
        new DetachedCriteria<PARITYAuthor>(PARITYAuthor).where {
            or {
                exists books.where { eq 'title', 'X' }.id()
            }
        }
    }

    static DetachedCriteria<PARITYAuthor> buildNested() {
        DetachedCriteria<PARITYBook> books = new DetachedCriteria<PARITYBook>(PARITYBook)
        new DetachedCriteria<PARITYAuthor>(PARITYAuthor).where {
            exists books.build { eq 'title', 'X' }.id()
        }
    }

    static DetachedCriteria<PARITYAuthor> inListClosure() {
        new DetachedCriteria<PARITYAuthor>(PARITYAuthor).where {
            inList('id') {
                eq 'name', 'X'
                projections { id() }
            }
        }
    }

    static DetachedCriteria<PARITYAuthor> gtAllClosure() {
        new DetachedCriteria<PARITYAuthor>(PARITYAuthor).where {
            gtAll('id') {
                eq 'name', 'X'
                projections { id() }
            }
        }
    }

    static DetachedCriteria<PARITYBook> projectionsProperty() {
        new DetachedCriteria<PARITYBook>(PARITYBook).where {
            projections { property 'title' }
        }
    }

    static DetachedCriteria<PARITYBook> projectionsCount() {
        new DetachedCriteria<PARITYBook>(PARITYBook).where {
            projections { count() }
        }
    }

    static DetachedCriteria<PARITYBook> projectionsAll() {
        new DetachedCriteria<PARITYBook>(PARITYBook).where {
            projections {
                id()
                property 'title'
                avg 'pages'
                sum 'pages'
                min 'pages'
                max 'pages'
                distinct()
            }
        }
    }
'''

    @CompileStatic
    static class NestedCriteriaFixture {

        DetachedCriteria<Author> outer
        DetachedCriteria<Book> inner
        DetachedCriteria<Book> bookQuery

        void existsWithEq() {
            DetachedCriteria<Book> books = new DetachedCriteria<Book>(Book)
            outer = new DetachedCriteria<Author>(Author).where {
                this.inner = books.where { eq 'title', 'X' }
                exists this.inner
            }
        }

        void notExistsWithEq() {
            DetachedCriteria<Book> books = new DetachedCriteria<Book>(Book)
            outer = new DetachedCriteria<Author>(Author).where {
                this.inner = books.where { eq 'title', 'X' }
                notExists this.inner
            }
        }

        void existsWithEqProperty() {
            DetachedCriteria<Book> books = new DetachedCriteria<Book>(Book)
            outer = new DetachedCriteria<Author>(Author).where {
                this.inner = books.where { eqProperty 'title', 'subtitle' }
                exists this.inner
            }
        }

        void whereQuerySyntax() {
            DetachedCriteria<Author> authors = new DetachedCriteria<Author>(Author)
            DetachedCriteria<Book> books = new DetachedCriteria<Book>(Book)
            outer = authors.where {
                exists books.where { title == 'X' }.id()
            }
        }

        void existsInsideOrJunction() {
            DetachedCriteria<Book> books = new DetachedCriteria<Book>(Book)
            outer = new DetachedCriteria<Author>(Author).where {
                or {
                    exists books.where { eq 'title', 'X' }.id()
                }
            }
        }

        void existsWithBuild() {
            DetachedCriteria<Book> books = new DetachedCriteria<Book>(Book)
            outer = new DetachedCriteria<Author>(Author).where {
                exists books.build { eq 'title', 'X' }.id()
            }
        }

        void inListClosureSubquery() {
            outer = new DetachedCriteria<Author>(Author).where {
                inList('id') {
                    eq 'name', 'X'
                    projections { id() }
                }
            }
        }

        void gtAllClosureSubquery() {
            outer = new DetachedCriteria<Author>(Author).where {
                gtAll('id') {
                    eq 'name', 'X'
                    projections { id() }
                }
            }
        }

        void projectionsProperty() {
            bookQuery = new DetachedCriteria<Book>(Book).where {
                projections { property 'title' }
            }
        }

        void projectionsCount() {
            bookQuery = new DetachedCriteria<Book>(Book).where {
                projections { count() }
            }
        }

        void hoistedControl() {
            inner = new DetachedCriteria<Book>(Book).where { eq 'title', 'X' }
            outer = new DetachedCriteria<Author>(Author).where {
                exists this.inner
            }
        }
    }

    @Entity
    static class Author {
        String name
    }

    @Entity
    static class Book {
        String title
        String subtitle
        Integer pages
    }
}
