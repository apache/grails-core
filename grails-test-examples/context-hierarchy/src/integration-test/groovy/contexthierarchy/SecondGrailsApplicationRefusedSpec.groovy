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

package contexthierarchy

import org.springframework.boot.WebApplicationType
import org.springframework.context.ConfigurableApplicationContext
import spock.lang.Specification

import grails.boot.GrailsAppBuilder
import grails.core.GrailsApplication
import contexthierarchy.shared.SharedConfiguration

/**
 * One hierarchy holds one Grails application. While the application runs, the parent holds its
 * {@code grailsApplication} on loan, so a second application class started as a sibling is refused
 * at startup with a message that says so, and the first is left running.
 */
class SecondGrailsApplicationRefusedSpec extends Specification {

    void 'a second Grails application beneath the same parent is refused'() {
        given: 'the shared parent with the Grails application already running beneath it'
        GrailsAppBuilder parentBuilder = new GrailsAppBuilder(SharedConfiguration)
        GrailsAppBuilder firstBuilder = parentBuilder.child(Application).properties('server.port=0')
        ConfigurableApplicationContext first = firstBuilder.run()

        when: 'another Grails application class is started as its sibling'
        firstBuilder.sibling(AnotherApplication).web(WebApplicationType.NONE).run()

        then:
        IllegalStateException e = thrown()
        e.message.contains('Only one context in a hierarchy can be the Grails application')

        and: 'the first application and the parent are unaffected'
        first.isActive()
        parentBuilder.context().isActive()
        first.getBean(GrailsApplication.APPLICATION_ID) != null

        cleanup:
        first?.close()
        parentBuilder.context()?.close()
    }
}
