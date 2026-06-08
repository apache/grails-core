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
package test

import grails.plugin.springsecurity.InterceptedUrl
import grails.plugin.springsecurity.web.access.intercept.RequestmapFilterInvocationDefinition
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

/**
 * Avoids the problem when using requestmaps where you can't make any web calls without any
 * instances in the database, but the instances are populated by a call to TestDataController.
 *
 * @author Burt Beckwith
 */
@Slf4j
@CompileStatic
class TestRequestmapFilterInvocationDefinition extends RequestmapFilterInvocationDefinition {

	private final List<String> permitAll = Collections.singletonList('permitAll')

	@Override
	protected void resetConfigs() {
		super.resetConfigs()

		for (String urlPattern in ['/error', '/hack/**', '/testdata/**']) {
			compileAndStoreMapping new InterceptedUrl(urlPattern, permitAll, null)
		}
	}
}
