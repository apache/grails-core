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
package grails.plugin.springsecurity

import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationListener
import org.springframework.security.access.event.AbstractAuthorizationEvent
import org.springframework.security.authentication.event.AbstractAuthenticationEvent
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent
import org.springframework.security.authentication.event.AuthenticationSuccessEvent
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent
import org.springframework.security.web.authentication.switchuser.AuthenticationSwitchUserEvent

import groovy.transform.CompileStatic

/**
 * Registers as an event listener and delegates handling of security-related events
 * to optional closures defined in Config.groovy.
 *
 * The following callbacks are supported:<br>
 * <ul>
 * <li>onInteractiveAuthenticationSuccessEvent</li>
 * <li>onAbstractAuthenticationFailureEvent</li>
 * <li>onAuthenticationSuccessEvent</li>
 * <li>onAuthenticationSwitchUserEvent</li>
 * <li>onAuthorizationEvent</li>
 * </ul>
 * All callbacks are optional; you can implement just the ones you're interested in, e.g.
 * <pre>
 * grails {
 *    plugin {
 *       springsecurity {
 *          ...
 *          onAuthenticationSuccessEvent = { e, appCtx -&gt;
 *             ...
 *          }
 *       }
 *    }
 * }
 * </pre>
 * The event and the Spring context are provided in case you need to look up a Spring bean,
 * e.g. the Hibernate SessionFactory.
 *
 * @author Burt Beckwith
 */
@CompileStatic
class SecurityEventListener implements ApplicationListener<ApplicationEvent>, ApplicationContextAware {

	ApplicationContext applicationContext

	void onApplicationEvent(ApplicationEvent e) {
		if (e instanceof AbstractAuthenticationEvent) {
			if (e instanceof InteractiveAuthenticationSuccessEvent) {
				call e, 'onInteractiveAuthenticationSuccessEvent'
			}
			else if (e instanceof AbstractAuthenticationFailureEvent) {
				call e, 'onAbstractAuthenticationFailureEvent'
			}
			else if (e instanceof AuthenticationSuccessEvent) {
				call e, 'onAuthenticationSuccessEvent'
			}
			else if (e instanceof AuthenticationSwitchUserEvent) {
				call e, 'onAuthenticationSwitchUserEvent'
			}
		}
		else if (e instanceof AbstractAuthorizationEvent) {
			call e, 'onAuthorizationEvent'
		}
	}

	@SuppressWarnings('rawtypes')
	protected void call(ApplicationEvent e, String closureName) {
		def closure = SpringSecurityUtils.securityConfig[closureName]
		if (closure instanceof Closure) {
			closure e, applicationContext
		}
 	}
}
