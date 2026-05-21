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
package org.grails.datastore.gorm.services.transform

import grails.gorm.annotation.Entity
import org.codehaus.groovy.ast.Variable
import org.codehaus.groovy.ast.VariableScope
import org.codehaus.groovy.ast.builder.AstBuilder
import org.codehaus.groovy.ast.expr.GStringExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.ReturnStatement
import org.codehaus.groovy.ast.tools.GeneralUtils
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.ErrorCollector
import org.codehaus.groovy.control.SourceUnit
import spock.lang.Specification
import static org.codehaus.groovy.ast.tools.GeneralUtils.*

/**
 * Created by graemerocher on 13/02/2017.
 */
class QueryStringTransformerSpec extends Specification {

    void 'test transform simple hql query'() {
        given:
        GStringExpression query = transformedQuery('from ${Book b} where $b.title = \'test\'')

        expect:
        query.values.size() == 0
        query.asConstantString().text == 'from org.grails.datastore.gorm.services.transform.Book as b where b.title = \'test\''
    }

    void 'test tranform hql with inner join declaration'() {
        given:
        GStringExpression query = transformedQuery('''\
from ${Book b} 
inner join ${Author a = b.author} 
where $b.title = $title and $a.name = $name''', varX("title"))

        expect:
        query.values.size() == 2
        query.asConstantString().text == '''from org.grails.datastore.gorm.services.transform.Book as b 
inner join b.author as a 
where b.title =  and a.name = '''

    }

    void 'test tranform hql with nested inner join declaration'() {
        given:
        GStringExpression query = transformedQuery('from ${Book b} inner join ${Publisher p = b.author.publisher} where $b.title = $title and $p.name = $name', varX("title"), varX('name'))

        expect:
        query.asConstantString().text == 'from org.grails.datastore.gorm.services.transform.Book as b inner join b.author.publisher as p where b.title =  and p.name = '
        query.values.size() == 2

    }
    void 'test transform hql query with arguments'() {
        given:
        GStringExpression query = transformedQuery('from ${Book b} where $b.title = $title', varX("title"))

        expect:
        query.values.size() == 1
        query.asConstantString().text == 'from org.grails.datastore.gorm.services.transform.Book as b where b.title = '
    }

    void 'test transform simple hql query with error'() {

        given:
        def sourceUnit = sourceUnit()
        transformedQuery(sourceUnit,'from ${Book b} where $b.titl = \'test\'')

        expect:
        sourceUnit.errorCollector.errorCount == 1
    }

    void 'test transform a hql query referencing a superclass property'() {
        given:
        def sourceUnit = sourceUnit()
        transformedQuery(sourceUnit,'from ${Publisher p} where ${p.country} is null')

        expect:
        sourceUnit.errorCollector.errorCount == 0
    }

    SourceUnit sourceUnit() {
        def config = new CompilerConfiguration(new Properties())
        def unit = new SourceUnit("test", 'test', config, new GroovyClassLoader(), new ErrorCollector(config))
        return unit
    }
    GStringExpression transformedQuery(String query, Variable...vars) {
        transformedQuery(null, query, vars)
    }


    GStringExpression transformedQuery(SourceUnit sourceUnit, String query, Variable...vars) {
        def nodes = new AstBuilder().buildFromString("""
import org.grails.datastore.gorm.services.transform.*
\"\"\"$query\"\"\"
""")

        BlockStatement statement = nodes[0]
        ReturnStatement returnS = statement.statements[0]
        GStringExpression gstring = returnS.expression

        def scope = new VariableScope()
        for(v in vars) scope.putDeclaredVariable(v)
        new QueryStringTransformer(sourceUnit, scope).transformQuery(gstring)
    }
}

@Entity
class Book {
    String title
    Author author
}

@Entity
class Author {
    String name
    Publisher publisher
}

@Entity
class Publisher extends AbstractPublisher {
    String name
}

abstract class AbstractPublisher {
    String country
}