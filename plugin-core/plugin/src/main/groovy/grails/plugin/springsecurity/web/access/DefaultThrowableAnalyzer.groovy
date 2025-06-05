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
package grails.plugin.springsecurity.web.access

import jakarta.servlet.ServletException

import org.springframework.security.web.util.ThrowableAnalyzer
import org.springframework.security.web.util.ThrowableCauseExtractor

import groovy.transform.CompileStatic

/**
 * Copy of org.springframework.security.web.access.ExceptionTranslationFilter.DefaultThrowableAnalyzer which is private.
 *
 * @author Burt Beckwith
 */
@CompileStatic
class DefaultThrowableAnalyzer extends ThrowableAnalyzer {

	@Override
	protected void initExtractorMap() {
		super.initExtractorMap()

		registerExtractor ServletException, new ThrowableCauseExtractor() {
			Throwable extractCause(Throwable throwable) {
				ThrowableAnalyzer.verifyThrowableHierarchy throwable, ServletException
				((ServletException)throwable).rootCause
			}
		}
	}
}
