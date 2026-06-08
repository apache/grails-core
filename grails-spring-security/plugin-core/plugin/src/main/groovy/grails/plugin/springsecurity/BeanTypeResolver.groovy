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

import grails.core.GrailsApplication
import groovy.transform.CompileStatic

/**
 * Used in doWithSpring to allow overriding of the class of individual Spring beans by setting a property in the config.
 * The property name syntax is beanName + 'BeanClass', so for example to override the type of the 'authoritiesMapper'
 * bean, add a property <code>authoritiesMapperBeanClass = 'com.foo.Bar'</code> or
 * <code>authoritiesMapperBeanClass = com.foo.Bar</code>.
 *
 * This is useful when a bean override retains all of the configuration options of the original and only the class is
 * different. Just overriding the class (ordinarily done with a bean post-processor) allows redefined beans to use new
 * or changed properties in future versions of the plugin.
 *
 * @author Burt Beckwith
 */
@CompileStatic
class BeanTypeResolver {

	protected ConfigObject conf
	protected GrailsApplication grailsApplication

	BeanTypeResolver(ConfigObject securityConfig, GrailsApplication application) {
		conf = securityConfig
		grailsApplication = application
	}

	Class resolveType(String beanName, Class defaultType) {
		def override = conf[beanName + 'BeanClass']
		if (override instanceof CharSequence) {
			override = Class.forName(override.toString(), false, Thread.currentThread().contextClassLoader)
		}
		override ?: defaultType
	}
}
