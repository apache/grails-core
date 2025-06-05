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

import groovy.transform.CompileStatic
import org.springframework.beans.BeansException
import org.springframework.beans.MutablePropertyValues
import org.springframework.beans.factory.config.BeanFactoryPostProcessor
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.beans.factory.config.RuntimeBeanReference
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.GenericBeanDefinition
import org.springframework.boot.web.servlet.FilterRegistrationBean

/**
 * Unregisters auto-config beans registered by Boot.
 *
 * @author Burt Beckwith
 */
@CompileStatic
class SpringSecurityBeanFactoryPostProcessor implements BeanFactoryPostProcessor {

	protected static final String AUTOCONFIG_NAME = 'org.springframework.boot.autoconfigure.security.SecurityFilterAutoConfiguration'
	protected static final String SECURITY_PROPERTIES_NAME = 'securityProperties'

	void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
		if (beanFactory instanceof BeanDefinitionRegistry) {
			removeAutoconfigBeans beanFactory
			disableFilterRegistrationBeans beanFactory
		}
	}

	protected void removeAutoconfigBeans(BeanDefinitionRegistry beanFactory) {
		if (beanFactory.containsBeanDefinition(AUTOCONFIG_NAME)) {
			beanFactory.removeBeanDefinition AUTOCONFIG_NAME
		}

		if (beanFactory.containsBeanDefinition(SECURITY_PROPERTIES_NAME)) {
			if (beanFactory.getBeanDefinition(SECURITY_PROPERTIES_NAME).factoryBeanName == AUTOCONFIG_NAME) {
				beanFactory.removeBeanDefinition SECURITY_PROPERTIES_NAME
			}
		}
	}

	/**
	 * Need to add a FilterRegistrationBean with enabled set to false to prevent Boot from
	 * registering all of the filters in the filterchains again as regular filters.
	 */
	protected void disableFilterRegistrationBeans(BeanDefinitionRegistry beanFactory) {
		SortedMap<Integer, String> filterNames = ReflectionUtils.findFilterChainNames(SpringSecurityUtils.securityConfig)
		for (String name in filterNames.values()) {
			beanFactory.registerBeanDefinition name + 'DeregistrationBean', new GenericBeanDefinition(
					beanClass: FilterRegistrationBean,
					propertyValues: new MutablePropertyValues(
							enabled: false,
							filter: new RuntimeBeanReference(name)))
		}
	}
}
