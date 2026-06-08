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
package grails.plugin.springsecurity.rest

import grails.plugin.springsecurity.SecurityEventListener
import groovy.transform.CompileStatic
import org.springframework.context.ApplicationContextAware
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationListener

/**
 * Registers as an event listener and delegates handling of security-related events
 * to optional closures defined in Config.groovy.
 * <p/>
 * The following callbacks are supported:<br/>
 * <ul>
 * <li>onRestTokenCreationEvent</li>
 * </ul>
 * All callbacks are optional; you can implement just the ones you're interested in, e.g.
 * <pre>
 * grails {
 *    plugin {
 *       springsecurity {
 *          ...
 *          onRestTokenCreationEvent = { e, appCtx ->
 *             ...
 *          }
 *       }
 *    }
 * }
 * </pre>
 * The event and the Spring context are provided in case you need to look up a Spring bean,
 * e.g. the Hibernate SessionFactory.
 *
 */
@CompileStatic
public class RestSecurityEventListener extends SecurityEventListener implements ApplicationListener<ApplicationEvent>, ApplicationContextAware {

    public void onApplicationEvent(final ApplicationEvent e) {
        if (e instanceof RestTokenCreationEvent) {
            call(e, "onRestTokenCreationEvent")
        } else {
            super.onApplicationEvent(e)
        }
    }

}
