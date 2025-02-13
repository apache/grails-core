/* Copyright 2006-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package grails.plugin.springsecurity.cas

import org.apereo.cas.client.proxy.ProxyGrantingTicketStorage

import groovy.transform.CompileStatic

/**
 * No-op implementation.
 *
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
@CompileStatic
class NullProxyGrantingTicketStorage implements ProxyGrantingTicketStorage {
	void cleanUp() {}
	String retrieve(String proxyGrantingTicketIou) {}
	void save(String proxyGrantingTicketIou, String proxyGrantingTicket) {}
}
