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
package grails.plugin.springsecurity.acl.access.method

import grails.plugin.springsecurity.acl.util.ProxyUtils
import grails.plugin.springsecurity.annotation.Secured
import groovy.transform.CompileStatic
import org.springframework.security.access.ConfigAttribute
import org.springframework.security.access.SecurityConfig
import org.springframework.security.access.method.AbstractFallbackMethodSecurityMetadataSource

import java.lang.annotation.Annotation
import java.lang.reflect.Method

/**
 * Based on the Spring Security class of the same name but supports the plugin's @Secured annotation
 * which can be applied on fields to support annotated controller actions.
 *
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
@CompileStatic
class SecuredAnnotationSecurityMetadataSource extends AbstractFallbackMethodSecurityMetadataSource {

	protected Collection<String> serviceClassNames

	@Override
	protected Collection<ConfigAttribute> findAttributes(Class<?> clazz) {
		Class<?> actualClass = ProxyUtils.unproxy(clazz)
		if (isService(actualClass)) {
			return processAnnotation(actualClass.getAnnotation(Secured))
		}
	}

	@Override
	protected Collection<ConfigAttribute> findAttributes(Method method, Class<?> targetClass) {
		Method actualMethod = ProxyUtils.unproxy(method)
		if (isService(actualMethod.declaringClass)) {
			return processAnnotation(actualMethod.getAnnotation(Secured))
		}
	}

	Collection<ConfigAttribute> getAllConfigAttributes() {}

	protected List<ConfigAttribute> processAnnotation(Annotation a) {
		if (a instanceof Secured) {
			a.value().collect { String token -> ((ConfigAttribute)new SecurityConfig(token)) }
		}
	}

	protected boolean isService(Class<?> clazz) {
		serviceClassNames.any { String name -> name == clazz.name }
	}

	/**
	 * Dependency injection for current service class names.
	 * @param names the names
	 */
	void setServiceClassNames(Collection<String> names) {
		serviceClassNames = names
	}
}
