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
package grails.plugin.springsecurity.annotation

import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.util.GrailsApplicationAttributes
import org.springframework.context.ApplicationContext
import org.springframework.security.access.PermissionEvaluator
import org.springframework.security.access.hierarchicalroles.RoleHierarchy
import org.springframework.security.authentication.AuthenticationTrustResolver
import org.springframework.security.core.Authentication
import org.springframework.security.web.FilterInvocation
import org.springframework.security.web.access.expression.WebSecurityExpressionRoot

import grails.web.servlet.mvc.GrailsParameterMap
import groovy.transform.CompileStatic

/**
 * Set as the delegate of a closure in @Secured annotations; provides access to the request and application context,
 * as well as all of the methods and properties available when using SpEL.
 *
 * @author Burt Beckwith
 */
@CompileStatic
class SecuredClosureDelegate extends WebSecurityExpressionRoot {

	ApplicationContext ctx

	SecuredClosureDelegate(Authentication a, FilterInvocation fi, ApplicationContext ctx) {
		super(a, fi)
		this.ctx = ctx
		setTrustResolver ctx.getBean('authenticationTrustResolver', AuthenticationTrustResolver)
		setRoleHierarchy ctx.getBean('roleHierarchy', RoleHierarchy)
		setPermissionEvaluator ctx.getBean('permissionEvaluator', PermissionEvaluator)
	}

	GrailsParameterMap getParams() {
		GrailsWebRequest gwr = (GrailsWebRequest)request.getAttribute(GrailsApplicationAttributes.WEB_REQUEST)
		gwr?.params
	}
}
