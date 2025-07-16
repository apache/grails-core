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
import spock.lang.Issue

/**
 * @author graemerocher
 */
class TransientsSpec extends GormDatastoreSpec {

    @Issue('https://github.com/grails/grails-data-mapping/issues/574')
    void "Test transients are actually transient"() {
        when:
        TransientChild child = new TransientChild(name:"Bob", transientProperty: "blah")
        child.save(flush:true)
        session.clear()
        child = TransientChild.get(child.id)

        then:
        child.name == "Bob"
        child.transientProperty == null
    }

    @Override
    List getDomainClasses() {
        [TransientParent, TransientChild]
    }
}

@Entity
class TransientParent {


    static mapWith = 'neo4j'
}

@Entity
class TransientChild extends TransientParent {
    String name
    String transientProperty

    String getTransientProperty() {
        return transientProperty
    }

    void setTransientProperty(String transientProperty) {
        this.transientProperty = transientProperty
    }
    static transients = ["transientProperty"]
}
