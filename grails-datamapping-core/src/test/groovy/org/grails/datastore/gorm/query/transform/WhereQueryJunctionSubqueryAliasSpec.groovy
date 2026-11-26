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
 * the CLASS_GENERATION phase, where that failure happened.
 */
@Issue('https://github.com/apache/grails-core/issues/17177')
class WhereQueryJunctionSubqueryAliasSpec extends Specification {

    private static void compile(String source) {
        CompilationUnit unit = new CompilationUnit(new GroovyClassLoader())
        unit.addSource('Source.groovy', source)
        unit.compile(Phases.CLASS_GENERATION)
    }

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
    ${declaration == 'a method' ? "static find() { ${query} }" : declaration == 'a property' ? "static found = ${query}" : "private static final Object FOUND = ${query}"}
}
"""

        when:
        compile(source)

        then:
        noExceptionThrown()

        where:
        [operator, declaration] << [['||', '&&'], ['a method', 'a property', 'a private field']].combinations()
    }
}
