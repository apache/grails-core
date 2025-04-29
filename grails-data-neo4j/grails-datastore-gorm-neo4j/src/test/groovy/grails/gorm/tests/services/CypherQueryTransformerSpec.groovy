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

package grails.gorm.tests.services

import org.codehaus.groovy.ast.Variable
import org.codehaus.groovy.ast.VariableScope
import org.codehaus.groovy.ast.builder.AstBuilder
import org.codehaus.groovy.ast.expr.GStringExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.ReturnStatement
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.ErrorCollector
import org.codehaus.groovy.control.SourceUnit
import org.grails.datastore.gorm.neo4j.services.transform.CypherQueryStringTransformer
import org.grails.datastore.gorm.services.transform.QueryStringTransformer
import spock.lang.Specification

import static org.codehaus.groovy.ast.tools.GeneralUtils.varX

/**
 * Created by graemerocher on 09/03/2017.
 */
class CypherQueryTransformerSpec extends Specification{

    void 'test transform cypher query on entity with arguments'() {
        given:
        GStringExpression query = transformedQuery('MATCH ${Book b} WHERE $b.title = $title', varX("title"))

        expect:
        query.values.size() == 1
        query.asConstantString().text == 'MATCH (b:Book) WHERE b.title = '
    }

    void 'test transform cypher update query'() {
        given:
        GStringExpression query = transformedQuery('''MATCH ${Book b} 
               WHERE $b.title = $name  
               SET b.age = $age''', varX("title"),varX("age"))

        expect:
        query.values.size() == 2
        query.asConstantString().text == '''MATCH (b:Book) 
               WHERE b.title =   
               SET b.age = '''
    }

    void 'test transform cypher query on entity with ID query'() {
        given:
        GStringExpression query = transformedQuery('MATCH ${Book b} WHERE ID($b) = $title', varX("title"))

        expect:
        query.values.size() == 1
        query.asConstantString().text == 'MATCH (b:Book) WHERE ID(b) = '
    }

    void 'test transform cypher query on relationship with arguments'() {
        given:
        GStringExpression query = transformedQuery('MATCH ${AuthorBooks b} WHERE $to.title = $title', varX("title"))

        expect:
        query.values.size() == 1
        query.asConstantString().text == 'MATCH (from:Author)-[b]->(to:Book) WHERE to.title = '
    }

    GStringExpression transformedQuery(String query, Variable...vars) {
        def mock = Mock(SourceUnit)
        mock.getErrorCollector() >> new ErrorCollector(new CompilerConfiguration())
        transformedQuery(mock, query, vars)
    }

    GStringExpression transformedQuery(SourceUnit sourceUnit, String query, Variable...vars) {
        def nodes = new AstBuilder().buildFromString("""
import grails.gorm.tests.services.*
\"\"\"$query\"\"\"
""")

        BlockStatement statement = nodes[0]
        ReturnStatement returnS = statement.statements[0]
        GStringExpression gstring = returnS.expression

        def scope = new VariableScope()
        for(v in vars) scope.putDeclaredVariable(v)
        new CypherQueryStringTransformer(sourceUnit, scope).transformQuery(gstring)
    }
}
