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

import org.grails.datastore.mapping.model.ClassMapping
import org.grails.datastore.mapping.model.ValueGenerator
import org.grails.orm.hibernate.cfg.HibernateCompositeIdentity
import org.grails.orm.hibernate.cfg.HibernateSimpleIdentity
import spock.lang.Specification

class HibernateIdentityMappingSpec extends Specification {

    void "getIdentifierName returns 'id' for HibernateSimpleIdentity with null name"() {
        given:
        def identity = new HibernateSimpleIdentity()
        identity.name = null
        def mapping = new HibernateIdentityMapping(identity, ValueGenerator.IDENTITY, Mock(ClassMapping))

        expect:
        mapping.getIdentifierName() == ['id'] as String[]
    }

    void "getIdentifierName returns custom name for HibernateSimpleIdentity with name set"() {
        given:
        def identity = new HibernateSimpleIdentity()
        identity.name = 'myId'
        def mapping = new HibernateIdentityMapping(identity, ValueGenerator.IDENTITY, Mock(ClassMapping))

        expect:
        mapping.getIdentifierName() == ['myId'] as String[]
    }

    void "getIdentifierName returns property names for HibernateCompositeIdentity"() {
        given:
        def identity = new HibernateCompositeIdentity()
        identity.propertyNames = ['firstName', 'lastName'] as String[]
        def mapping = new HibernateIdentityMapping(identity, ValueGenerator.ASSIGNED, Mock(ClassMapping))

        expect:
        mapping.getIdentifierName() == ['firstName', 'lastName'] as String[]
    }

    void "getIdentifierName returns 'id' for unrecognized identity type"() {
        given:
        def mapping = new HibernateIdentityMapping(new Object(), ValueGenerator.NATIVE, Mock(ClassMapping))

        expect:
        mapping.getIdentifierName() == ['id'] as String[]
    }

    void "getGenerator returns the configured generator"() {
        given:
        def mapping = new HibernateIdentityMapping(new HibernateSimpleIdentity(), ValueGenerator.SEQUENCE, Mock(ClassMapping))

        expect:
        mapping.getGenerator() == ValueGenerator.SEQUENCE
    }

    void "getClassMapping returns the configured classMapping"() {
        given:
        def classMapping = Mock(ClassMapping)
        def mapping = new HibernateIdentityMapping(new HibernateSimpleIdentity(), ValueGenerator.IDENTITY, classMapping)

        expect:
        mapping.getClassMapping() == classMapping
    }

    void "getMappedForm returns the identity object"() {
        given:
        def identity = new HibernateSimpleIdentity()
        def mapping = new HibernateIdentityMapping(identity, ValueGenerator.IDENTITY, Mock(ClassMapping))

        expect:
        mapping.getMappedForm() == identity
    }
}
