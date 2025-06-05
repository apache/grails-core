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
package grails.plugin.springsecurity.web.access.intercept

import grails.plugin.springsecurity.InterceptedUrl
import grails.plugin.springsecurity.ReflectionUtils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

/**
 * @author Burt Beckwith
 */
@Slf4j
@CompileStatic
class InterceptUrlMapFilterInvocationDefinition extends AbstractFilterInvocationDefinition {

	@Override
	protected void initialize() {
		if (!initialized) {
			reset()
		}
	}

	@Override
	protected boolean stopAtFirstMatch() {
		true
	}

	@SuppressWarnings('unchecked')
	@Override
	void reset() {
		def interceptUrlMap = ReflectionUtils.getConfigProperty('interceptUrlMap')

		if (interceptUrlMap instanceof Map) {
			throw new IllegalArgumentException("interceptUrlMap defined as a Map is not supported; must be specified as a " +
					"List of Maps as described in section 'Configuring Request Mappings to Secure URLs' of the reference documentation")
		}

		if (!(interceptUrlMap instanceof List)) {
			log.warn "interceptUrlMap config property isn't a List of Maps"
			return
		}

		resetConfigs()

		ReflectionUtils.splitMap((List<Map<String, Object>>)interceptUrlMap).each { InterceptedUrl iu -> compileAndStoreMapping iu }

		initialized = true

		log.trace 'configs: {}', configAttributeMap
	}
}
