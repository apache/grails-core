/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.grails.data.testing.tck.tests

import org.apache.grails.data.testing.tck.domains.ContactDetails
import org.apache.grails.data.testing.tck.domains.Patient
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.grails.datastore.mapping.proxy.ProxyHandler
import spock.lang.PendingFeatureIf

class BuiltinUniqueConstraintWorksWithTargetProxiesConstraintsSpec extends GrailsDataTckSpec {

    void setupSpec() {
        manager.domainClasses.addAll([ContactDetails, Patient])
    }

    @PendingFeatureIf({ !Boolean.getBoolean('hibernate5.gorm.suite') && !Boolean.getBoolean('hibernate6.gorm.suite') && !Boolean.getBoolean('mongodb.gorm.suite')})
    void "test unique constraint on root instance"() {

        setup:
        ContactDetails contactDetails1 = new ContactDetails(phoneNumber: "+1-202-555-0105").save(failOnError: true)
        ContactDetails contactDetails2 = new ContactDetails(phoneNumber: "+1-202-555-0105")
        manager.session.flush()
        manager.session.clear()

        when: "I try to validate the another object"
        contactDetails2.validate()

        then: "another should have an error on name because it is duplicated"
        contactDetails2.hasErrors()
        contactDetails2.errors.hasFieldErrors("phoneNumber")
        contactDetails2.errors.getFieldError("phoneNumber").codes.contains("unique.phoneNumber")

        cleanup:
        ContactDetails.deleteAll(contactDetails1)
    }

    @PendingFeatureIf({ !Boolean.getBoolean('hibernate5.gorm.suite') && !Boolean.getBoolean('hibernate6.gorm.suite') && !Boolean.getBoolean('mongodb.gorm.suite')})
    void "test unique constraint for the associated child object"() {

        setup:
        ContactDetails contactDetails1 = new ContactDetails(phoneNumber: "+1-202-555-0105").save(failOnError: true)
        Patient patient1 = new Patient(contactDetails: contactDetails1).save(failOnError: true)
        manager.session.flush()
        manager.session.clear()

        when:
        Patient patient2 = new Patient(contactDetails: new ContactDetails(phoneNumber: "+1-202-555-0105"))
        patient2.validate()

        then:
        patient2.hasErrors()
        patient2.errors.hasFieldErrors("contactDetails.phoneNumber")
        patient2.errors.getFieldError("contactDetails.phoneNumber").codes.contains("unique.phoneNumber")

        cleanup:
        Patient.deleteAll(patient1)
        ContactDetails.deleteAll(contactDetails1)
    }

    void "test unique constraint on the unmodified association loaded as initialized proxy"() {

        setup:
        final ProxyHandler proxyHandler = manager.session.mappingContext.getProxyHandler()
        ContactDetails contactDetails = new ContactDetails(phoneNumber: "+1-202-555-0105").save(failOnError: true)
        Patient patient = new Patient(contactDetails: contactDetails).save(failOnError: true)
        Long patientId = patient.id
        manager.session.flush()
        manager.session.clear()

        when:
        patient = Patient.get(patientId)
        patient.contactDetails.phoneNumber = "+1-202-555-0105"

        then:
        proxyHandler.isProxy(patient.contactDetails)

        expect:
        patient.validate()

        cleanup:
        Patient.deleteAll(patient)
        ContactDetails.deleteAll(patient.contactDetails)
    }
}
