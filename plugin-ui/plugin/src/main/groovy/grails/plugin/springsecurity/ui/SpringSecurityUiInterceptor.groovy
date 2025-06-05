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
package grails.plugin.springsecurity.ui

import grails.plugin.springsecurity.ReflectionUtils
import grails.plugin.springsecurity.SpringSecurityUtils
import grails.plugin.springsecurity.ui.strategy.PropertiesStrategy
import groovy.transform.CompileStatic
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.ModelAndView

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

/**
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
@CompileStatic
class SpringSecurityUiInterceptor implements HandlerInterceptor {

	/** Dependency injection for the 'uiPropertiesStrategy' bean. */
	PropertiesStrategy uiPropertiesStrategy

	boolean preHandle(HttpServletRequest request, HttpServletResponse response, handler) { true }

	void postHandle(HttpServletRequest request, HttpServletResponse response, handler, ModelAndView modelAndView) {
		Map<String, Object> model = modelAndView?.model
		if (model != null) {
			def conf = SpringSecurityUtils.securityConfig
			model.layoutRegister = ReflectionUtils.getConfigProperty('ui.gsp.layoutRegister') ?: 'register'
			model.layoutUi = ReflectionUtils.getConfigProperty('ui.gsp.layoutUi') ?: 'springSecurityUI'
			model.securityConfig = conf
			model.uiPropertiesStrategy = uiPropertiesStrategy
		}
	}

	void afterCompletion(HttpServletRequest request, HttpServletResponse response, handler, Exception e) {}
}
