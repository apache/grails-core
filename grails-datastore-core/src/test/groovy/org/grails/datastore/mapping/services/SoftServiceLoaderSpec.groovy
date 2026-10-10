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
package org.grails.datastore.mapping.services

import org.slf4j.helpers.NOPLogger
import spock.lang.Specification

class SoftServiceLoaderSpec extends Specification {

    void "service definitions are read from META-INF services ignoring comments and blank lines"() {
        when:
        List<ServiceDefinition<SslService>> definitions = SoftServiceLoader.load(SslService).toList()

        then:
        definitions*.name == [SslServiceA.name, 'org.grails.datastore.mapping.services.SslMissing', SslServiceB.name]
        definitions*.present == [true, false, true]
        definitions[0].type == SslServiceA
        definitions[0].load() instanceof SslServiceA
        definitions[2].type == SslServiceB
    }

    void "definitions are cached so a second iteration yields the same instances"() {
        given:
        SoftServiceLoader<SslService> loader = SoftServiceLoader.load(SslService, SslService.classLoader)

        when:
        List first = loader.toList()
        List second = loader.toList()

        then:
        first.size() == 3
        (0..2).every { first[it].is(second[it]) }

        when:
        Iterator iterator = loader.iterator()
        3.times { iterator.next() }
        iterator.next()

        then:
        thrown(NoSuchElementException)
    }

    void "a condition filters service names before they are loaded"() {
        when:
        List<ServiceDefinition<SslService>> definitions =
                SoftServiceLoader.load(SslService, SslService.classLoader, { String name -> name.endsWith('B') }).toList()

        then:
        definitions*.name == [SslServiceB.name]
        SoftServiceLoader.load(SslService, null, null).toList().size() == 3
    }

    void "first and firstOr return the first definition or an alternative"() {
        expect:
        SoftServiceLoader.load(SslService).first().get().name == SslServiceA.name
        SoftServiceLoader.load(SslService).firstOr('ignored', SslService.classLoader).get().name == SslServiceA.name
        !SoftServiceLoader.load(SslUnregistered).first().present
        !SoftServiceLoader.load(SslUnregistered).firstOr('no.such.Alternative', SslService.classLoader).present

        when:
        ServiceDefinition<SslUnregistered> alternative =
                SoftServiceLoader.load(SslUnregistered).firstOr(SslUnregisteredImpl.name, SslService.classLoader).get()

        then:
        alternative.name == SslUnregisteredImpl.name
        alternative.present
        alternative.load() instanceof SslUnregisteredImpl
    }

    void "missing services report their absence"() {
        given:
        ServiceDefinition<SslService> missing = SoftServiceLoader.load(SslService).toList()[1]

        when:
        missing.type

        then:
        ServiceConfigurationError e = thrown()
        e.message == "Call to load() when class 'org.grails.datastore.mapping.services.SslMissing' is not present"

        when:
        missing.load()

        then:
        e = thrown()
        e.message == "Call to load() when class 'org.grails.datastore.mapping.services.SslMissing' is not present"

        when:
        missing.orElseThrow { new IllegalStateException('absent') }

        then:
        IllegalStateException ise = thrown()
        ise.message == 'absent'
    }

    void "present services instantiate or throw the supplied exception when instantiation fails"() {
        given:
        List<ServiceDefinition<SslService>> definitions = SoftServiceLoader.load(SslService).toList()

        expect:
        definitions[0].orElseThrow { new IllegalStateException('unused') } instanceof SslServiceA

        when:
        definitions[2].orElseThrow { new IllegalStateException('cannot construct') }

        then:
        IllegalStateException e = thrown()
        e.message == 'cannot construct'

        when:
        definitions[2].load()

        then:
        ServiceConfigurationError sce = thrown()
        sce.message.startsWith('Error loading service [' + SslServiceB.name + ']: ')
        sce.cause != null
    }

    void "class loader logging is disabled unless the system property is set"() {
        expect:
        SoftServiceLoader.getLogger(String).is(NOPLogger.NOP_LOGGER)
        SoftServiceLoader.REFLECTION_LOGGER.is(NOPLogger.NOP_LOGGER)
        SoftServiceLoader.PROPERTY_GRAILS_CLASSLOADER_LOGGING == 'grails.classloader.logging'
        SoftServiceLoader.META_INF_SERVICES == 'META-INF/services'
    }

}

interface SslService { }

interface SslUnregistered { }

class SslServiceA implements SslService { }

class SslServiceB implements SslService {

    SslServiceB() {
        throw new IllegalStateException('no instances')
    }

}

class SslUnregisteredImpl implements SslUnregistered { }
