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
package grails.gorm.tests

import org.apache.grails.data.neo4j.core.Neo4jGormDatastoreSpec
import spock.lang.Issue

/**
 * A junction without criteria adds nothing to the Cypher, and a block without criteria inside an or still asks for an
 * associated node, as it does on the datastores that join.
 */
@Issue('14635')
class EmptyJunctionSpec extends Neo4jGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(NullTestFolder)
    }

    private void seed() {
        NullTestFolder other = new NullTestFolder(name: 'Other').save(flush: true, failOnError: true)
        new NullTestFolder(name: 'Root', parent: other).save(flush: true, failOnError: true)
        NullTestFolder.withSession { it.clear() }
    }

    void "an empty or beside a property adds nothing"() {
        given:
        seed()

        when:
        List<NullTestFolder> folders = NullTestFolder.createCriteria().list {
            or {}
            eq('name', 'Root')
        }

        then:
        folders*.name == ['Root']
    }

    void "an empty and beside a property adds nothing"() {
        given:
        seed()

        when:
        List<NullTestFolder> folders = NullTestFolder.createCriteria().list {
            and {}
            eq('name', 'Root')
        }

        then:
        folders*.name == ['Root']
    }

    void "an empty or alone matches every node"() {
        given:
        seed()

        when:
        List<NullTestFolder> folders = NullTestFolder.createCriteria().list { or {} }

        then:
        folders*.name.sort() == ['Other', 'Root']
    }

    void "an empty association block in an or still requires an associated node"() {
        given:
        seed()

        when:
        List<NullTestFolder> folders = NullTestFolder.createCriteria().list {
            or {
                parent {}
                eq('name', 'Other')
            }
        }

        then: 'the block adds no branch, and the node without a parent is left out'
        folders.isEmpty()
    }

    void "a double negation of a block that holds only an empty junction still requires the associated node"() {
        given:
        seed()

        when: 'the inner negation is the absence of a parent, and the outer one asks for a parent again'
        List<NullTestFolder> folders = NullTestFolder.createCriteria().list {
            not { not { parent { and {} } } }
        }

        then: 'only Root has a parent: Other, which has none, is not accepted by the outer negation'
        folders*.name == ['Root']
    }

    void "an odd number of negations of a block that holds only an empty junction matches no node, as on Hibernate 5"() {
        given:
        seed()

        expect: 'the block is a join that requires a parent, and what it asks of the parent is true: its negation is false'
        NullTestFolder.createCriteria().list { not { parent { and {} } } }.isEmpty()
        NullTestFolder.createCriteria().list { not { not { not { parent { and {} } } } } }.isEmpty()
    }
}
