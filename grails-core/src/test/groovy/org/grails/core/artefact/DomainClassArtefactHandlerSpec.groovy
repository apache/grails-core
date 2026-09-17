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
package org.grails.core.artefact

import org.springframework.core.Ordered
import spock.lang.Specification
import spock.lang.Unroll

import grails.artefact.Artefact
import grails.core.GrailsClass
import org.grails.core.DefaultGrailsDomainClass

class DomainClassArtefactHandlerSpec extends Specification {

    DomainClassArtefactHandler handler = new DomainClassArtefactHandler()

    void 'type and plugin name are Domain / domainClass'() {
        expect:
        handler.type == DomainClassArtefactHandler.TYPE
        handler.type == 'Domain'
        handler.pluginName == DomainClassArtefactHandler.PLUGIN_NAME
        handler.pluginName == 'domainClass'
    }

    void 'order is highest precedence'() {
        expect:
        handler.order == Ordered.HIGHEST_PRECEDENCE
    }

    void 'setGrailsApplication is a no-op accepted for GrailsApplicationAware compliance'() {
        when:
        handler.setGrailsApplication(null)

        then:
        noExceptionThrown()
    }

    void 'newArtefactClass wraps the class in a DefaultGrailsDomainClass'() {
        when:
        GrailsClass grailsClass = handler.newArtefactClass(PlainDomain)

        then:
        grailsClass instanceof DefaultGrailsDomainClass
        grailsClass.clazz == PlainDomain
    }

    @Unroll
    void 'isArtefactClass(#label) == #expected'() {
        expect:
        handler.isArtefactClass(clazz) == expected

        where:
        label                        | clazz               || expected
        '@Artefact("Domain")'        | ArtefactAnnotated   || true
        '@grails.persistence.Entity' | GrailsEntity        || true
        '@jakarta.persistence.Entity'| JakartaEntity       || true
        'plain class'                | PlainDomain         || false
        'null'                       | null                || false
    }

    void 'isDomainClass(Class, allowProxyClass) falls back to the superclass for a CGLIB-style proxy name'() {
        expect:
        !DomainClassArtefactHandler.isDomainClass(DomainProxy$$EnhancerByProxy, false)
        DomainClassArtefactHandler.isDomainClass(DomainProxy$$EnhancerByProxy, true)
    }

    static class PlainDomain {

    }

    @Artefact('Domain')
    static class ArtefactAnnotated {

    }

    @grails.persistence.Entity
    static class GrailsEntity {

    }

    @jakarta.persistence.Entity
    static class JakartaEntity {

    }

}

// Top-level so getSimpleName() reflects the literal "$$" rather than a nested-class separator.
class DomainProxy$$EnhancerByProxy extends DomainClassArtefactHandlerSpec.GrailsEntity {

}
