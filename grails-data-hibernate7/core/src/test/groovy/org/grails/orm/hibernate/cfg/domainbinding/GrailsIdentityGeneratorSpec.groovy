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

package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.tests.HibernateGormDatastoreSpec
import org.apache.grails.data.testing.tck.domains.ChildEntity
import org.apache.grails.data.testing.tck.domains.TestEntity
import org.grails.orm.hibernate.cfg.HibernateSimpleIdentity
import org.hibernate.generator.GeneratorCreationContext
import org.hibernate.mapping.BasicValue
import org.hibernate.mapping.Column
import org.hibernate.mapping.Property
import org.hibernate.mapping.Table
import org.hibernate.type.Type
import spock.lang.Subject

import org.grails.orm.hibernate.cfg.domainbinding.generator.GrailsIdentityGenerator

class GrailsIdentityGeneratorSpec extends HibernateGormDatastoreSpec {

    @Override
    void setupSpec() {
        manager.registerDomainClasses(TestEntity, ChildEntity)
    }

    def "should configure identity generator and set column as identity"() {
        given:
        def context = Mock(GeneratorCreationContext)
        def mappedId = new HibernateSimpleIdentity()
        mappedId.setParams([foo: 'bar'])
        
        def table = new Table("test")
        def hibernateProperty = new Property()
        def value = new BasicValue(getGrailsDomainBinder().getMetadataBuildingContext(), table)
        def column = new Column("test_id")
        value.addColumn(column)
        hibernateProperty.setValue(value)

        context.getProperty() >> hibernateProperty
        context.getType() >> Stub(Type) {
            getReturnedClass() >> Long
        }
        
        when:
        @Subject
        def generator = new GrailsIdentityGenerator(context, mappedId)

        then:
        column.isIdentity() == true
        generator != null
    }

    def "should handle null mappedId gracefully"() {
        given:
        def context = Mock(GeneratorCreationContext)
        
        def table = new Table("test")
        def hibernateProperty = new Property()
        def value = new BasicValue(getGrailsDomainBinder().getMetadataBuildingContext(), table)
        def column = new Column("test_id2")
        value.addColumn(column)
        hibernateProperty.setValue(value)

        context.getProperty() >> hibernateProperty
        context.getType() >> Stub(Type) {
            getReturnedClass() >> Long
        }
        
        when:
        @Subject
        def generator = new GrailsIdentityGenerator(context, null)

        then:
        column.isIdentity() == true
        generator != null
    }
}
