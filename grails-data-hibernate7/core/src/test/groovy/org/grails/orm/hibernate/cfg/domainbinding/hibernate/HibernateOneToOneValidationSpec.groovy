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
package org.grails.orm.hibernate.cfg.domainbinding.hibernate

import spock.lang.Specification
import org.hibernate.MappingException
import org.grails.orm.hibernate.cfg.PropertyConfig

class HibernateOneToOneValidationSpec extends Specification {

    def "HibernateOneToOneProperty validation exception for unidirectional hasOne"() {
        given: "A stub representing a unidirectional hasOne"
        def stub = new TestHibernateOneToOneProperty(hasOne: true, bidirectional: false, name: "myHasOne")

        when:
        stub.validateAssociation()

        then:
        def e = thrown(MappingException)
        e.message == "hasOne property [myHasOne] is not bidirectional. Specify the other side of the relationship!"
    }

    static class TestHibernateOneToOneProperty extends HibernateOneToOneProperty {
        boolean hasOne
        boolean bidirectional
        String name

        TestHibernateOneToOneProperty(Map args = [:]) {
            super(null, null, new java.beans.PropertyDescriptor(args.name ?: "test", TestHibernateOneToOneProperty, "getName", null))
            this.hasOne = args.hasOne ?: false
            this.bidirectional = args.bidirectional ?: false
            this.name = args.name ?: "test"
        }

        @Override boolean isHasOne() { hasOne }
        @Override boolean isBidirectional() { bidirectional }
        @Override String getName() { name }
        
        @Override PropertyConfig getMappedForm() { null }
    }
}
