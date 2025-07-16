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

package org.grails.datastore.gorm.mongo

import grails.gorm.annotation.Entity
import grails.neo4j.Neo4jEntity
import org.neo4j.driver.types.Node
import spock.lang.Ignore

import jakarta.persistence.FlushModeType

/**
 * @author Graeme Rocher
 */
@Ignore
class ReadManyObjectsSpec extends GormDatastoreSpec {



    void "Test that reading thousands of objects natively performs well"() {
        given:"A lot of test data"
        createData()
        session.flush()
        session.clear()

        when:"The data is read"
        final now = System.currentTimeMillis()
        1000.times {

            final cursor = ProfileDoc.cypherStatic('MATCH (n:ProfileDoc) RETURN n')
            for(p in cursor) {
                Node n = p.n.asNode()
                def n1 = n.get('n1')
                def n2 = n.get('n2')
                def n3 = n.get('n3')
                def date = n.get('date')
            }
            print "Iteration $it "
        }
        final then = System.currentTimeMillis()
        long took = then-now
        println "Took ${then-now}ms"

        then:"If it gets to this point we "
        took < 30000

    }

    void "Test that reading thousands of objects with GORM performs well"() {
        given:"A lot of test data"
        createData()
        session.flush()
        session.clear()

        session.setFlushMode(FlushModeType.COMMIT)
        when:"The data is read"
        long took = 30000
        final now = System.currentTimeMillis()
        1000.times {

            for(p in ProfileDoc.list()) {
                def n1 = p.n1
                def n2 = p.n2
                def n3 = p.n3
                def date = p.date
            }
            print "Iteration $it "
            session.clear()
        }
        final then = System.currentTimeMillis()
        took = then-now
        println "Took ${then-now}ms"

        then:"Check that it doesn't take too long"
        took < 30000

    }

    void createData() {
        1000.times {
            new ProfileDoc(n1:"Plane $it".toString(),n2:it,n3:it.toLong(), date: new Date()).save()
        }
    }

    @Override
    List getDomainClasses() {
        [ProfileDoc]
    }
}

@Entity
class ProfileDoc implements Neo4jEntity<ProfileDoc> {
    Long id
    String n1
    Integer n2
    Long n3
    Date date

    static mapping = {
        autowire false
    }
}
