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
import org.springframework.boot.web.server.context.WebServerApplicationContext
import org.springframework.context.ConfigurableApplicationContext
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise
import spock.lang.Tag

import grails.boot.GrailsApp
import grails.boot.GrailsAppBuilder
import grails.boot.config.GrailsEarlyPluginRegistrationPostProcessor
import grails.core.GrailsApplication
import grails.plugins.GrailsPluginManager
import org.apache.grails.testing.http.client.HttpClientSupport
import contexthierarchy.shared.AuditTrail
import contexthierarchy.shared.SharedConfiguration

/**
 * The arrangement {@link Application#main} uses: a plain Spring parent holding shared infrastructure,
 * and the Grails application as its child, serving web requests with controllers, GSP and GORM.
 *
 * <p>The hierarchy is started here through the builder rather than through {@code @Integration},
 * which boots the application class on its own and would not see the parent.</p>
 */
@Stepwise
@Tag('http-client')
class SharedParentGrailsChildSpec extends Specification implements HttpClientSupport {

    @Shared
    GrailsAppBuilder parentBuilder

    @Shared
    GrailsAppBuilder childBuilder

    @Shared
    ConfigurableApplicationContext parent

    @Shared
    ConfigurableApplicationContext child

    @Shared
    int port

    void setupSpec() {
        parentBuilder = new GrailsAppBuilder(SharedConfiguration)
        childBuilder = parentBuilder.child(Application).properties('server.port=0')
        child = childBuilder.run()
        parent = parentBuilder.context()
        port = ((WebServerApplicationContext) child).webServer.port
    }

    void setup() {
        httpLocalServerPort = port
    }

    void cleanupSpec() {
        if (child?.isActive()) {
            child.close()
        }
        if (parent?.isActive()) {
            parent.close()
        }
    }

    void 'the Grails application is the child of the shared parent, and both were started by a GrailsApp'() {
        expect:
        child.parent.is(parent)
        child instanceof WebServerApplicationContext
        childBuilder.application() instanceof GrailsApp
        parentBuilder.application() instanceof GrailsApp

        and: 'the parent cannot host a web server, as in Spring Boot'
        parentBuilder.application().webApplicationType == WebApplicationType.NONE
        !(parent instanceof WebServerApplicationContext)
    }

    void 'the plugin lifecycle ran in the Grails child only'() {
        expect:
        child.beanFactory.containsSingleton(GrailsEarlyPluginRegistrationPostProcessor.EARLY_REGISTRATION_COMPLETE_BEAN_NAME)
        child.beanFactory.containsSingleton(GrailsPluginManager.BEAN_NAME)
        !parent.beanFactory.containsSingleton(GrailsEarlyPluginRegistrationPostProcessor.EARLY_REGISTRATION_COMPLETE_BEAN_NAME)

        and: 'the parent holds the application of the child on loan while it runs'
        parent.getBean(GrailsApplication.APPLICATION_ID).is(child.getBean(GrailsApplication.APPLICATION_ID))
    }

    void 'the application reports the hierarchy it runs in'() {
        when:
        def response = http('/hierarchy/contexts')

        then:
        response.assertStatus(200)
        def json = response.json()
        json.parentPresent == true
        json.auditTrailInParent == true
        json.auditTrailLocal == false
        json.grailsApplicationLocal == true
    }

    void 'a controller injects a bean of the parent context and renders a GSP with it'() {
        when:
        def response = http('/hierarchy/greet?name=Ada')

        then:
        response.assertStatus(200)
        String html = response.body() as String
        html.contains('<h1 id="greeting">Hello Ada</h1>')
        html.contains('<li>greeted Ada</li>')

        and: 'the controller wrote to the very instance the parent holds'
        parent.getBean(AuditTrail).entries.contains('greeted Ada')
    }

    void 'the Grails service and GORM work in the child as in any Grails application'() {
        when:
        def first = http('/hierarchy/record?name=Grace')
        def second = http('/hierarchy/record?name=Linus')

        then:
        first.assertStatus(200)
        first.json().count == 1
        second.assertStatus(200)
        second.json().count == 2
        second.json().names == ['Grace', 'Linus']
        parent.getBean(AuditTrail).entries.containsAll(['registered Grace', 'registered Linus'])
    }

    void 'closing the Grails child returns the loan to the parent, which stays up'() {
        when:
        child.close()

        then:
        parent.isActive()
        !parent.containsBean(GrailsApplication.APPLICATION_ID)
        !parent.containsBean(GrailsPluginManager.BEAN_NAME)
        parent.getBean(AuditTrail).entries.contains('greeted Ada')
    }
}
