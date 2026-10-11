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
package org.grails.datastore.gorm.neo4j

import spock.lang.Specification

class CypherBuilderSpec extends Specification {

    void "a bare builder matches the labels and returns the node"() {
        expect:
        new CypherBuilder(':Person').build() == 'MATCH (n:Person) RETURN n as data\n'
        CypherBuilder.DEFAULT_RETURN_STATEMENT == ' RETURN n as data\n'
        CypherBuilder.buildRelationship(':A', '-[:KNOWS]->', ':B') == '(from:A)-[:KNOWS]->(to:B)'
        CypherBuilder.buildRelationshipMatch(':A', '-[:KNOWS]->', ':B') == 'MATCH (from:A)-[:KNOWS]->(to:B) WHERE '
        CypherBuilder.buildRelationshipMatch('(a)-[r]->(b)') == 'MATCH (a)-[r]->(b) WHERE '
    }

    void "matches, conditions, optional matches and ordering are assembled in order"() {
        given:
        CypherBuilder builder = new CypherBuilder(':Person')

        when:
        builder.addRelationshipMatch('-[r:KNOWS]->(m:Person)')
        builder.addRelationshipMatch('-[r:KNOWS]->(m:Person)')
        builder.addMatch('(a:Address)')
        builder.addMatch('(a:Address)')
        builder.addMatch('(b:Address)')
        builder.addMatch('(x:Other)')
        builder.addMatch('(n)-[:AT]->(a:Address)')
        builder.conditions = 'n.name = $1'
        builder.addOptionalMatch(' (n)-[:LIVES_AT]->(a)')
        builder.orderAndLimits = 'ORDER BY n.name LIMIT 5'
        int first = builder.addParam('Fred')
        int second = builder.addParam(42)
        builder.replaceParamAt(2, 43)

        then:
        first == 1
        second == 2
        builder.nextMatchNumber == 4
        builder.params == ['1': 'Fred', '2': 43]
        builder.build() == 'MATCH (n:Person)-[r:KNOWS]->(m:Person), (a:Address), (b:Address), (x:Other) WHERE n.name = $1 \nOPTIONAL MATCH (n)-[:LIVES_AT]->(a) RETURN n as data\nORDER BY n.name LIMIT 5 \n'
    }

    void "explicit return columns and a custom start node change the return clause"() {
        given:
        CypherBuilder builder = new CypherBuilder(':Person')
        builder.startNode = 'p'
        builder.startNode = null

        when:
        String defaulted = builder.build()
        builder.addReturnColumn('p.name')
        builder.addReturnColumn('count(p)')
        builder.orderAndLimits = 'ORDER BY p.name'

        then:
        defaulted == 'MATCH (p:Person) RETURN p as data\n'
        builder.build() == 'MATCH (p:Person) RETURN p.name, count(p) ORDER BY p.name'
    }

    void "relationship matches can be replaced and property sets are parameterised"() {
        given:
        CypherBuilder builder = new CypherBuilder(':Person')

        when:
        builder.replaceFirstRelationshipMatch('-[r1]->(a)')
        builder.replaceFirstRelationshipMatch('-[r2]->(b)')
        builder.addPropertySet(null)
        builder.addPropertySet([name: 'Fred'])

        then:
        builder.params == ['1': [name: 'Fred']]
        builder.build() == 'MATCH (n:Person)-[r2]->(b)\nSET n += {1}\n RETURN n as data\n'
    }

    void "delete columns short circuit the statement"() {
        given:
        CypherBuilder builder = new CypherBuilder(':Person')
        builder.conditions = 'n.name = $1'
        builder.addDeleteColumn('n')
        builder.addDeleteColumn('r')
        builder.addReturnColumn('ignored')

        expect:
        builder.build() == 'MATCH (n:Person) WHERE n.name = $1\n DETACH DELETE n, r'
    }

}
