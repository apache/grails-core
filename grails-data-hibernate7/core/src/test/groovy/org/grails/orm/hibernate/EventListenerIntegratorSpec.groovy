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
package org.grails.orm.hibernate

import org.hibernate.boot.Metadata
import org.hibernate.boot.spi.BootstrapContext
import org.hibernate.engine.spi.SessionFactoryImplementor
import org.hibernate.event.service.spi.EventListenerRegistry
import org.hibernate.service.spi.SessionFactoryServiceRegistry
import spock.lang.Specification

class EventListenerIntegratorSpec extends Specification {

    def "test that integrate throws IllegalStateException if EventListenerRegistry is not available"() {
        given:
        HibernateEventListeners hibernateEventListeners = Mock(HibernateEventListeners)
        Map<String, Object> eventListeners = [:]
        EventListenerIntegrator integrator = new EventListenerIntegrator(hibernateEventListeners, eventListeners)

        Metadata metadata = Mock(Metadata)
        BootstrapContext bootstrapContext = Mock(BootstrapContext)
        SessionFactoryImplementor sfi = Mock(SessionFactoryImplementor)
        SessionFactoryServiceRegistry serviceRegistry = Mock(SessionFactoryServiceRegistry)

        when:
        integrator.integrate(metadata, bootstrapContext, sfi)

        then:
        1 * sfi.getServiceRegistry() >> serviceRegistry
        1 * serviceRegistry.getService(EventListenerRegistry) >> null
        
        def e = thrown(IllegalStateException)
        e.message == "EventListenerRegistry not available from ServiceRegistry"
    }
}
