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
package grails.plugin.springsecurity.acl

import grails.core.GrailsApplication
import grails.core.GrailsClass
import grails.util.GrailsClassUtils
import grails.util.GrailsNameUtils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.grails.compiler.injection.GrailsAwareClassLoader
import org.grails.core.artefact.ControllerArtefactHandler
import org.grails.core.artefact.ServiceArtefactHandler
import org.grails.spring.TypeSpecifyableTransactionProxyFactoryBean
import org.springframework.aop.TargetSource
import org.springframework.aop.framework.autoproxy.AbstractAutoProxyCreator
import org.springframework.aop.target.SingletonTargetSource
import org.springframework.beans.BeansException
import org.springframework.beans.factory.InitializingBean
import org.springframework.security.access.prepost.PostAuthorize
import org.springframework.security.access.prepost.PostFilter
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.access.prepost.PreFilter

import java.lang.annotation.Annotation
import java.lang.reflect.Method

/**
 * Based on https://github.com/alkemist/grails-aop-reloading-fix/blob/master/src/groovy/grails/plugin/aopreloadingfix/ClassLoaderPerProxyGroovyAwareAspectJAwareAdvisorAutoProxyCreator.groovy
 * and https://github.com/grails-plugins/grails-spring-security-acl/pull/8
 *
 * @author Luke Daley
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 * @author Predrag Knezevic
 */
@Slf4j
@CompileStatic
class AclAutoProxyCreator extends AbstractAutoProxyCreator implements InitializingBean {

	private static final long serialVersionUID = 1

	protected GrailsApplication grailsApplication
	protected ClassLoader baseLoader
	protected Collection<String> serviceBeanNames = []

	@SuppressWarnings('unchecked')
	protected final Class<? extends Annotation>[] ANNOTATIONS = [
		grails.plugin.springsecurity.annotation.Secured,
		org.springframework.security.access.annotation.Secured,
		PreAuthorize, PreFilter, PostAuthorize, PostFilter] as Class[]

	@Override
	protected Object[] getAdvicesAndAdvisorsForBean(Class<?> beanClass, String beanName, TargetSource customTargetSource) throws BeansException {
		if ((serviceBeanNames.contains(beanName) && (beanClass != TypeSpecifyableTransactionProxyFactoryBean)) || shouldProxy(beanClass, beanName)) {
			return PROXY_WITHOUT_ADDITIONAL_INTERCEPTORS
		}
		return DO_NOT_PROXY
	}

	protected boolean beanIsAnnotated(Class<?> c) {
		for (Class<? extends Annotation> annotation in ANNOTATIONS) {
			if (c.isAnnotationPresent(annotation)) {
				return true
			}

			for (Method method in c.methods) {
				if (method.isAnnotationPresent(annotation)) {
					return true
				}
			}
		}
	}

	@Override
	protected boolean shouldProxyTargetClass(Class<?> beanClass, String beanName) {
		(serviceBeanNames.contains(beanName) && (beanClass != TypeSpecifyableTransactionProxyFactoryBean)) ||
		super.shouldProxyTargetClass(beanClass, beanName)
	}

	@Override
	protected getCacheKey(Class<?> beanClass, String beanName) {
		beanClass.hashCode() + '_' + beanName
	}

	protected boolean shouldProxy(Class<?> c, String beanName) {

		if (grailsApplication.isArtefactOfType(ControllerArtefactHandler.TYPE, c)) {
			// pre and post annotations don't make sense, and @Secured is handled by url checks
			return false
		}

		boolean hasSpringSecurityACL = GrailsClassUtils.isStaticProperty(c, 'springSecurityACL')
		if (hasSpringSecurityACL || beanIsAnnotated(c)) {
			log.debug 'Secure "{}" instances of {}', beanName, c.name
			return true
		}
	}

	@Override
	protected createProxy(Class<?> beanClass, String beanName, Object[] specificInterceptors, TargetSource targetSource) {
		try {
			setProxyClassLoader new GrailsAwareClassLoader(baseLoader, null, false)
			if (beanClass == TypeSpecifyableTransactionProxyFactoryBean) {
				try {
					TypeSpecifyableTransactionProxyFactoryBean bean = (TypeSpecifyableTransactionProxyFactoryBean)targetSource.target
					beanClass = bean.objectType
					targetSource = new SingletonTargetSource(bean.object)
				}
				catch (e) {
					log.error 'Failed to getobject type inside createProxy', e
				}
			}
			super.createProxy beanClass, beanName, specificInterceptors, targetSource
		}
		finally {
			setProxyClassLoader baseLoader
		}
	}

	@Override
	void setBeanClassLoader(ClassLoader classLoader) {
		super.setBeanClassLoader classLoader
		baseLoader = classLoader
	}

	void afterPropertiesSet() {
		for (GrailsClass serviceClass in grailsApplication.getArtefacts(ServiceArtefactHandler.TYPE)) {
			String beanName = GrailsNameUtils.getPropertyNameRepresentation(serviceClass.clazz.name)
			if (shouldProxy(serviceClass.clazz, beanName)) {
				serviceBeanNames << beanName
			}
		}
	}

	/**
	 * Dependency injection for the application.
	 * @param application the application
	 */
	void setGrailsApplication(GrailsApplication application) {
		grailsApplication = application
	}
}
