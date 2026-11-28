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

import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.Phases

import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Unroll

/**
 * A where query whose {@code ||} or {@code &&} has a subquery on one side that uses the alias of the enclosing query
 * compiles. The transform rewrote the closures after their variable scopes were computed, and the closure it creates
 * for the {@code or} or {@code and} had no scope of its own, so the class generator failed with a
 * {@code NullPointerException} looking for the alias the inner closure still listed. The source is compiled to
 * the CLASS_GENERATION phase, where that failure happened, from every place a class can hold such a query: a method, a
 * constructor, an initializer block, a property, a field, a closure in any of them, and a nested or anonymous class.
 *
 * <p>The scopes of the method or constructor are computed again for the class that holds it, never for the class a where
 * query named: a field of the class that has the name of a property of the queried class is still the field after the
 * query, in a statically compiled class too, from every place above and for a field, a property and a static field.</p>
 */
@Issue('https://github.com/apache/grails-core/issues/17177')
class WhereQueryJunctionSubqueryAliasSpec extends Specification {

    private static void compile(String source) {
        CompilationUnit unit = new CompilationUnit(new GroovyClassLoader())
        unit.addSource('Source.groovy', source)
        unit.compile(Phases.CLASS_GENERATION)
    }

    private static final Map<String, Closure<String>> DECLARATIONS = [
        'a method'                          : { String q -> "static find() { ${q} }" },
        'a property'                        : { String q -> "static found = ${q}" },
        'a private field'                   : { String q -> "private static final Object FOUND = ${q}" },
        'a constructor'                     : { String q -> "JunctionBook() { def found = ${q} }" },
        'a constructor with a parameter'    : { String q -> "JunctionBook(String name) { this.title = name; def found = ${q} }" },
        'an instance initializer block'     : { String q -> "{ def found = ${q} }" },
        'a static initializer block'        : { String q -> "static { def found = ${q} }" },
        'a closure in a method'             : { String q -> "static find() { def run = { -> ${q} }; run() }" },
        'a closure in a constructor'        : { String q -> "JunctionBook() { def run = { -> ${q} }; run() }" },
        'a closure field'                   : { String q -> "private Closure run = { -> ${q} }" },
        'a method of a nested class'        : { String q -> "static class Helper { static find() { ${q} } }" },
        'a constructor of a nested class'   : { String q -> "static class Helper { Helper() { def found = ${q} } }" },
        'an initializer block of a nested class': { String q -> "static class Helper { { def found = ${q} } }" },
        'a method of an anonymous class'    : { String q -> "static find() { new Object() { def search() { ${q} } } }" },
        'an initializer block of an anonymous class': { String q -> "static find() { new Object() { { def found = ${q} } } }" },
        'a closure in a method of an anonymous class': { String q -> "static find() { new Object() { def search() { def run = { -> ${q} }; run() } } }" },
    ]

    @Unroll
    void "a where query with a subquery that uses the enclosing alias on one side of #operator compiles in #declaration"() {
        given:
        String query = "JunctionBook.where { def t = JunctionBook; title == 'x' ${operator} exists(JunctionBook.where { id == t.other }.property('title')) }"
        String source = """
import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.query.transform.ApplyDetachedCriteriaTransform

@ApplyDetachedCriteriaTransform
@Entity
class JunctionBook {
    String title
    JunctionBook other
    ${DECLARATIONS[declaration].call(query)}
}
"""

        when:
        compile(source)

        then:
        noExceptionThrown()

        where:
        [operator, declaration] << [['||', '&&'], DECLARATIONS.keySet().toList()].combinations()
    }

    private static final Map<String, Closure<String>> FIELD_DECLARATIONS = [
        'a field'        : { String init -> "private String t = '${init}'" },
        'a property'     : { String init -> "String t = '${init}'" },
        'a static field' : { String init -> "private static String t = '${init}'" },
    ]

    private static final Map<String, Closure<String>> BODIES = [
        'a constructor'                    : { String q, String read -> "String found\n    JunctionBook() { def run = { -> ${q} }; found = ${read} }" },
        'a constructor with a parameter'   : { String q, String read -> "String found\n    JunctionBook(String name) { def run = { -> ${q} }; found = ${read} }" },
        'a method'                         : { String q, String read -> "String found\n    String find() { def run = { -> ${q} }; ${read} }" },
        'an instance initializer block'    : { String q, String read -> "String found\n    { def run = { -> ${q} }; found = ${read} }" },
        'a closure in a constructor'       : { String q, String read -> "String found\n    JunctionBook() { def read = { -> def run = { -> ${q} }; ${read} }; found = read() }" },
        'a constructor, run directly'      : { String q, String read -> "String found\n    JunctionBook() { try { def run = ${q} } catch (Throwable ignored) { }; found = ${read} }" },
        'a constructor with a parameter, run directly': { String q, String read -> "String found\n    JunctionBook(String name) { try { def run = ${q} } catch (Throwable ignored) { }; found = ${read} }" },
        'a method, run directly'           : { String q, String read -> "String found\n    String find() { try { def run = ${q} } catch (Throwable ignored) { }; ${read} }" },
        'an initializer block, run directly': { String q, String read -> "String found\n    { try { def run = ${q} } catch (Throwable ignored) { }; found = ${read} }" },
    ]

    @Unroll
    void "the field t is read as the field of the class, not of the queried class, after a where query that uses an alias t on one side of #operator in #body with #field read as #read"() {
        given:
        String query = "JunctionBook.where { def t = JunctionBook; title == 'x' ${operator} exists(JunctionBook.where { id == t.other }.property('title')) }"
        String source = """
import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.query.transform.ApplyDetachedCriteriaTransform

@ApplyDetachedCriteriaTransform
@Entity
class JunctionBook {
    String title
    JunctionBook other
    ${FIELD_DECLARATIONS[field].call('text')}
    ${BODIES[body].call(query, read)}
}
"""
        GroovyClassLoader loader = new GroovyClassLoader()
        Class<?> type = loader.parseClass(source)

        when:
        def instance = type.getDeclaredConstructors().find { it.parameterCount <= 1 }.with {
            parameterCount == 0 ? newInstance() : newInstance('name')
        }
        String result = body.startsWith('a method') ? instance.find() : instance.found

        then:
        result == 'text'

        where:
        [operator, body, field, read] << [['||', '&&'], BODIES.keySet().toList(), FIELD_DECLARATIONS.keySet().toList(),
                                          ['t', 'this.t']].combinations()
    }

    private static final Map<String, Closure<String>> QUERIES_ON_NUMERIC_T = [
        'a comparison'                     : { String op -> "JunctionItem.where { t > 5 }" },
        'a comparison and a second one'    : { String op -> "JunctionItem.where { t > 5 ${op} t < 1 }" },
        'a comparison and a subquery'      : { String op -> "JunctionItem.where { t > 5 ${op} exists(JunctionItem.where { id == 1 }.property('t')) }" },
        'an alias and a comparison'        : { String op -> "JunctionItem.where { def a = JunctionItem; t > 5 ${op} exists(JunctionItem.where { id == a.id }.property('t')) }" },
        'a nested association comparison'  : { String op -> "JunctionItem.where { t > 5 ${op} (owner.title == 'x') }" },
    ]

    @Unroll
    void "the text field t of the class that holds a where query on a numeric t of the queried class is a String, in a statically compiled #body, #query on #operator, #field"() {
        given:
        String source = """
import grails.gorm.annotation.Entity
import groovy.transform.CompileStatic
import org.grails.datastore.gorm.query.transform.ApplyDetachedCriteriaTransform

@Entity
class JunctionItem {
    Integer t
    JunctionBook owner
}

@ApplyDetachedCriteriaTransform
@Entity
@CompileStatic
class JunctionBook {
    String title
    ${FIELD_DECLARATIONS[field].call('text')}
    ${BODIES[body].call(QUERIES_ON_NUMERIC_T[query].call(operator), 't.toUpperCase().toLowerCase()')}
}
"""
        GroovyClassLoader loader = new GroovyClassLoader()
        loader.parseClass(source)
        Class<?> type = loader.loadClass('JunctionBook')

        when:
        def instance = type.getDeclaredConstructors().find { it.parameterCount <= 1 }.with {
            parameterCount == 0 ? newInstance() : newInstance('name')
        }
        String result = body.startsWith('a method') ? instance.find() : instance.found

        then:
        result == 'text'

        where:
        [query, operator, body, field] << [['a comparison', 'a comparison and a second one', 'a nested association comparison'],
                                           ['||', '&&'], BODIES.keySet().toList(), FIELD_DECLARATIONS.keySet().toList()].combinations()
    }

    @Unroll
    void "the field t of the class that holds a where query on a numeric t of the queried class is read as its own, with #query on #operator in #body, #field read as #read"() {
        given:
        String source = """
import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.query.transform.ApplyDetachedCriteriaTransform

@Entity
class JunctionItem {
    Integer t
    JunctionBook owner
}

@ApplyDetachedCriteriaTransform
@Entity
class JunctionBook {
    String title
    ${FIELD_DECLARATIONS[field].call('text')}
    ${BODIES[body].call(QUERIES_ON_NUMERIC_T[query].call(operator), read)}
}
"""
        GroovyClassLoader loader = new GroovyClassLoader()
        Class<?> type = loader.parseClass(source)
        type = loader.loadClass('JunctionBook')

        when:
        def instance = type.getDeclaredConstructors().find { it.parameterCount <= 1 }.with {
            parameterCount == 0 ? newInstance() : newInstance('name')
        }
        String result = body.startsWith('a method') ? instance.find() : instance.found

        then:
        result == 'text'

        where:
        [query, operator, body, field, read] << [QUERIES_ON_NUMERIC_T.keySet().toList(), ['||', '&&'], BODIES.keySet().toList(),
                                                 FIELD_DECLARATIONS.keySet().toList(), ['t', 'this.t']].combinations()
    }

    private static final Map<String, Closure<String>> NESTED_THIS_QUERIES = [
        'inside a comparison'     : { String op -> "JunctionItem.where { t > 5 ${op} exists(this.where { title == 'x' }.property('title')) }" },
        'as the only subquery'    : { String op -> "JunctionItem.where { exists(this.where { title == 'x' }.property('title')) }" },
        'with an alias'           : { String op -> "JunctionItem.where { def a = JunctionItem; t > 5 ${op} exists(this.where { title == a.id }.property('title')) }" },
    ]

    @Unroll
    void "a where query on this, inside a where query on a class that lacks the property, is checked against the class that holds it, #query on #operator in #body"() {
        given:
        String source = """
import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.query.transform.ApplyDetachedCriteriaTransform

@Entity
class JunctionItem {
    Integer t
    JunctionBook owner
}

@ApplyDetachedCriteriaTransform
@Entity
class JunctionBook {
    String title
    ${BODIES[body].call(NESTED_THIS_QUERIES[query].call(operator), "'text'")}
}
"""

        when:
        compile(source)

        then:
        noExceptionThrown()

        where:
        [query, operator, body] << [NESTED_THIS_QUERIES.keySet().toList(), ['||', '&&'], BODIES.keySet().toList()].combinations()
    }

    private static final Map<String, Closure<String>> WHERE_QUERY_HOLDERS = [
        'a static field'                 : { String q -> "static final DetachedCriteria field = ${q}" },
        'a static field with a modifier' : { String q -> "private static DetachedCriteria field = ${q}" },
        'a static property'              : { String q -> "static field = ${q}" },
        'a field'                        : { String q -> "def field = ${q}" },
    ]

    private static final Map<String, Closure<String>> WHERE_QUERIES_ON_ANOTHER_CLASS = [
        'where'                          : { String op -> "HolderItem.where { t > 5 ${op} t < 1 }" },
        'whereLazy'                      : { String op -> "HolderItem.whereLazy { t > 5 ${op} t < 1 }" },
        'where with a subquery'          : { String op -> "HolderItem.where { t > 5 ${op} exists(HolderItem.where { id == 1 }.property('t')) }" },
        'where with a subquery on this'  : { String op -> "HolderItem.where { t > 5 ${op} exists(HolderBook.where { title == 'x' }.property('title')) }" },
    ]

    @Unroll
    void "a #query that a #holder holds is on the class it names, not on the class that holds it, with #operator"() {
        given:
        String source = """
import grails.gorm.annotation.Entity
import grails.gorm.DetachedCriteria
import org.grails.datastore.gorm.query.transform.ApplyDetachedCriteriaTransform

@Entity
class HolderItem {
    Integer t
}

@ApplyDetachedCriteriaTransform
@Entity
class HolderBook {
    String title
    ${WHERE_QUERY_HOLDERS[holder].call(WHERE_QUERIES_ON_ANOTHER_CLASS[query].call(operator))}
}
"""

        when:
        compile(source)

        then:
        noExceptionThrown()

        where:
        [holder, query, operator] << [WHERE_QUERY_HOLDERS.keySet().toList(), WHERE_QUERIES_ON_ANOTHER_CLASS.keySet().toList(),
                                      ['||', '&&']].combinations()
    }

    @Unroll
    void "a #query that a #holder holds is a detached criteria of the class it names, with #operator"() {
        given:
        String source = """
import grails.gorm.annotation.Entity
import grails.gorm.DetachedCriteria
import org.grails.datastore.gorm.query.transform.ApplyDetachedCriteriaTransform

@Entity
class HolderItem {
    Integer t
}

@ApplyDetachedCriteriaTransform
@Entity
class HolderBook {
    String title
    ${WHERE_QUERY_HOLDERS[holder].call(WHERE_QUERIES_ON_ANOTHER_CLASS[query].call(operator))}
}
"""

        when:
        GroovyClassLoader loader = new GroovyClassLoader()
        loader.parseClass(source)
        Class<?> type = loader.loadClass('HolderBook')

        then: 'the field is a detached criteria of the queried class, as its generic type says'
        type.getDeclaredField('field').genericType.actualTypeArguments*.typeName == ['HolderItem']

        where:
        [holder, query, operator] << [WHERE_QUERY_HOLDERS.keySet().findAll { it != 'a field' }.toList(), ['where', 'whereLazy'],
                                      ['||', '&&']].combinations()
    }
}
