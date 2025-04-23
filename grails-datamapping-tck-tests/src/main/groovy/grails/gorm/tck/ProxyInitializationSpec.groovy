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

package grails.gorm.tck

import grails.gorm.tests.GormDatastoreSpec
import org.grails.datastore.mapping.proxy.ProxyHandler

class ProxyInitializationSpec extends GormDatastoreSpec {

    @Override
    List getDomainClasses() {
        [Patient, ContactDetails]
    }

    void "test if proxy is initialized"() {

        setup:
        final ProxyHandler proxyHandler = session.mappingContext.getProxyHandler()
        ContactDetails contactDetails = new ContactDetails(phoneNumber: "+1-202-555-0178").save(failOnError: true)
        Long patientId = new Patient(contactDetails: contactDetails).save(failOnError: true).id
        session.flush()
        session.clear()

        when:
        Patient patient = Patient.get(patientId)

        then:
        proxyHandler.isProxy(patient.contactDetails)

        when:
        patient.contactDetails.phoneNumber = "+1-202-555-0178"

        then:
        proxyHandler.isInitialized(patient.contactDetails)

        cleanup:
        Patient.deleteAll(patient)
        ContactDetails.deleteAll(patient.contactDetails)
    }
}
