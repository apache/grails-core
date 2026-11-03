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

import grails.gorm.annotation.Entity
import org.apache.grails.data.neo4j.core.Neo4jGormDatastoreSpec
import spock.lang.Issue

/**
 * An association is stored as a relationship, so isNull and isNotNull on it test for the absence and presence of
 * the relationship and not for a property of the node.
 */
@Issue('16584')
class IsNullAssociationSpec extends Neo4jGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(NullTestFolder)
    }

    private void seed() {
        NullTestFolder other = new NullTestFolder(name: 'Other').save(flush: true, failOnError: true)
        new NullTestFolder(name: 'Root', parent: other).save(flush: true, failOnError: true)
        NullTestFolder.withSession { it.clear() }
    }

    void "isNull on an association matches the nodes without the relationship"() {
        given:
        seed()

        when:
        List<NullTestFolder> folders = NullTestFolder.createCriteria().list { isNull('parent') }

        then:
        folders*.name == ['Other']
    }

    void "isNotNull on an association matches the nodes with the relationship"() {
        given:
        seed()

        when:
        List<NullTestFolder> folders = NullTestFolder.createCriteria().list { isNotNull('parent') }

        then:
        folders*.name == ['Root']
    }

    void "isNull on an association combined with a property criterion"() {
        given:
        seed()

        when:
        List<NullTestFolder> folders = NullTestFolder.createCriteria().list {
            isNull('parent')
            eq('name', 'Root')
        }

        then:
        folders.isEmpty()
    }

    void "isNull on a plain property still tests the node property"() {
        given:
        new NullTestFolder(name: 'Named').save(flush: true, failOnError: true)
        new NullTestFolder(name: null).save(flush: true, failOnError: true)
        NullTestFolder.withSession { it.clear() }

        when:
        List<NullTestFolder> folders = NullTestFolder.createCriteria().list { isNull('name') }

        then:
        folders.size() == 1
        folders[0].name == null
    }
}

@Entity
class NullTestFolder {
    String name
    NullTestFolder parent

    static constraints = {
        name nullable: true
        parent nullable: true
    }
}
