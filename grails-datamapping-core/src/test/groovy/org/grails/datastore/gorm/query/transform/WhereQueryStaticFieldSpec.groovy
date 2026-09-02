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
 * A static field on a domain class initialised from {@code Domain.where { ... }} or
 * {@code Domain.whereLazy { ... }} is handled specially by
 * {@code DetachedCriteriaTransformer#visitField}: the whole field initializer is replaced with
 * {@code new DetachedCriteria(Domain).build(closure)} (or {@code .buildLazy(closure)}), bypassing GORM's
 * dynamic {@code where} method entirely. That means the field is a real, usable
 * {@link grails.gorm.DetachedCriteria} the moment the class is initialised - no live datastore required
 * for the flat criteria used here - which also makes a static field initializer body a convenient,
 * self-contained place to exercise the different statement kinds
 * {@code DetachedCriteriaTransformer#addStatementToNewQuery} and
 * {@code DetachedCriteriaTransformer#flattenStatementIfNecessary} support: if/else, for, while, switch,
 * try/catch/finally, return, plain declarations and alias declarations.
 */
class WhereQueryStaticFieldSpec extends Specification {

    // The domain class name must be unique across the test JVM because
    // AstPropertyResolveUtils caches resolved properties statically by class name
    private static final String SOURCE = '''
import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity

@Entity
class StmtKindsQueryBook {
    String title
    BigDecimal price

    static DetachedCriteria<StmtKindsQueryBook> lazyQuery = StmtKindsQueryBook.whereLazy {
        title == "Deferred"
    }

    static DetachedCriteria<StmtKindsQueryBook> ifElseQuery = StmtKindsQueryBook.where {
        if (true) {
            title == "IfBranch"
        } else {
            price > 1
        }
    }

    static DetachedCriteria<StmtKindsQueryBook> forLoopQuery = StmtKindsQueryBook.where {
        for (i in 0..1) {
            title == "Loop"
        }
    }

    static DetachedCriteria<StmtKindsQueryBook> whileLoopQuery = StmtKindsQueryBook.where {
        while (false) {
            title == "Never"
        }
    }

    static DetachedCriteria<StmtKindsQueryBook> switchQuery = StmtKindsQueryBook.where {
        switch (1) {
            case 1:
                title == "CaseOne"
        }
    }

    static DetachedCriteria<StmtKindsQueryBook> tryCatchFinallyQuery = StmtKindsQueryBook.where {
        try {
            title == "TryBlock"
        } catch (Exception e) {
            price > 1
        } finally {
            title == "FinallyBlock"
        }
    }

    static DetachedCriteria<StmtKindsQueryBook> returnStatementQuery = StmtKindsQueryBook.where {
        return title == "Returned"
    }

    static DetachedCriteria<StmtKindsQueryBook> declarationPassthroughQuery = StmtKindsQueryBook.where {
        def x = 42
        title == "Declared"
    }

    static DetachedCriteria<StmtKindsQueryBook> classAliasQuery = StmtKindsQueryBook.where {
        def a = StmtKindsQueryBook
        title == "Aliased"
    }
}
'''

    private static Class<?> compile() {
        new GroovyClassLoader().parseClass(SOURCE)
    }

    void "a whereLazy static field defers its criteria until first accessed"() {
        given:
        grails.gorm.DetachedCriteria lazy = compile().lazyQuery

        expect: 'nothing has been built yet'
        lazy.criteria.empty

        when: 'a criterion is added, which triggers the deferred criteria to be applied first'
        lazy.add(new Query.Equals('price', 1))

        then:
        lazy.criteria.size() == 2
        lazy.criteria[0] instanceof Query.Equals
        ((Query.Equals) lazy.criteria[0]).property == 'title'
        ((Query.Equals) lazy.criteria[0]).value == 'Deferred'
        lazy.criteria[1] instanceof Query.Equals
        ((Query.Equals) lazy.criteria[1]).property == 'price'
    }

    void "an if/else statement only builds the criteria for the branch actually taken"() {
        given:
        grails.gorm.DetachedCriteria query = compile().ifElseQuery

        expect:
        query.criteria.size() == 1
        query.criteria[0] instanceof Query.Equals
        ((Query.Equals) query.criteria[0]).property == 'title'
        ((Query.Equals) query.criteria[0]).value == 'IfBranch'
    }

    void "a for statement builds one criterion per loop iteration"() {
        given:
        grails.gorm.DetachedCriteria query = compile().forLoopQuery

        expect:
        query.criteria.size() == 2
        query.criteria.every { it instanceof Query.Equals && it.property == 'title' && it.value == 'Loop' }
    }

    void "a while statement whose body never runs builds no criteria"() {
        given:
        grails.gorm.DetachedCriteria query = compile().whileLoopQuery

        expect:
        query.criteria.empty
    }

    void "a switch statement builds the criteria for the matched case"() {
        given:
        grails.gorm.DetachedCriteria query = compile().switchQuery

        expect:
        query.criteria.size() == 1
        ((Query.Equals) query.criteria[0]).property == 'title'
        ((Query.Equals) query.criteria[0]).value == 'CaseOne'
    }

    void "a try/finally block builds criteria for both the try body and the finally body"() {
        given:
        grails.gorm.DetachedCriteria query = compile().tryCatchFinallyQuery

        expect: 'the catch is compiled but never executes since the try body does not throw'
        query.criteria.size() == 2
        ((Query.Equals) query.criteria[0]).value == 'TryBlock'
        ((Query.Equals) query.criteria[1]).value == 'FinallyBlock'
    }

    void "a return statement builds the criteria for the returned expression"() {
        given:
        grails.gorm.DetachedCriteria query = compile().returnStatementQuery

        expect:
        query.criteria.size() == 1
        ((Query.Equals) query.criteria[0]).value == 'Returned'
    }

    void "a plain declaration statement is left untouched and does not prevent later criteria from being built"() {
        given:
        grails.gorm.DetachedCriteria query = compile().declarationPassthroughQuery

        expect:
        query.criteria.size() == 1
        ((Query.Equals) query.criteria[0]).value == 'Declared'
    }

    void "assigning the domain class itself to a variable sets the query alias"() {
        given:
        grails.gorm.DetachedCriteria query = compile().classAliasQuery

        expect:
        query.alias == 'a'
        query.criteria.size() == 1
        ((Query.Equals) query.criteria[0]).value == 'Aliased'
    }

    void "assigning an existing property name to a variable inside a where block compiles"() {
        expect: 'the createAlias-generating declaration branch compiles cleanly; it is not executed here ' +
                'because Criteria#createAlias needs a GORM-enhanced entity, which this module has none of'
        new GroovyClassLoader().parseClass('''
import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity

@Entity
class StmtKindsPropertyAliasBook {
    String title

    static DetachedCriteria<StmtKindsPropertyAliasBook> propertyAliasQuery = StmtKindsPropertyAliasBook.where {
        def t = title
        title == "Aliased"
    }
}
''')
    }

    void "negating a non-binary expression inside an if block still fails to compile"() {
        when:
        new GroovyClassLoader().parseClass('''
import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.query.transform.ApplyDetachedCriteriaTransform

@ApplyDetachedCriteriaTransform
@Entity
class StmtKindsInvalidIfBook {
    String title

    static findInvalid() {
        StmtKindsInvalidIfBook.where {
            if (true) {
                !(title)
            }
        }
    }
}
''')

        then:
        MultipleCompilationErrorsException e = thrown()
        e.message.contains('You can only negate a binary expressions in queries')
    }
}
