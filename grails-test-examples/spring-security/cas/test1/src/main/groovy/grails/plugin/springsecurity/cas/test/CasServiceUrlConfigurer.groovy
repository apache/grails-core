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

package grails.plugin.springsecurity.cas.test

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.apereo.cas.client.validation.Cas20ServiceTicketValidator
import org.springframework.boot.web.server.context.WebServerInitializedEvent
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationListener
import org.springframework.security.cas.ServiceProperties

/**
 * Rewrites the two CAS URLs that depend on the port the embedded server binds.
 *
 * <p>Integration tests run on a random port, so the service URL and the proxy callback URL cannot
 * be known when the CAS plugin defines its beans. Both are read per request rather than cached at
 * startup, so setting them once the server is up - but before it serves anything - is enough.</p>
 */
@Slf4j
@CompileStatic
class CasServiceUrlConfigurer implements ApplicationListener<WebServerInitializedEvent> {

    @Override
    void onApplicationEvent(WebServerInitializedEvent event) {
        int port = event.webServer.port
        ApplicationContext context = event.applicationContext

        ServiceProperties serviceProperties = context.getBean('casServiceProperties', ServiceProperties)
        serviceProperties.service = CasTestConfig.serviceUrl(port)
        log.info('CAS service URL set to {}', serviceProperties.service)

        if (CasTestConfig.proxyEnabled) {
            Cas20ServiceTicketValidator ticketValidator =
                    context.getBean('casTicketValidator', Cas20ServiceTicketValidator)
            ticketValidator.proxyCallbackUrl = CasTestConfig.proxyCallbackUrl(port)
            log.info('CAS proxy callback URL set to {}', CasTestConfig.proxyCallbackUrl(port))
        }
    }
}
