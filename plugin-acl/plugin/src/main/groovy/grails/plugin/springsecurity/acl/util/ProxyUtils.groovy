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
package grails.plugin.springsecurity.acl.util

import groovy.transform.CompileStatic
import org.springframework.util.ClassUtils
import org.springframework.util.ReflectionUtils

import java.lang.reflect.Constructor
import java.lang.reflect.Method

/**
 * Utility methods for unproxying classes.
 *
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
@CompileStatic
class ProxyUtils {

	private ProxyUtils() {
		// static only
	}

	/**
	 * Finds the unproxied superclass if proxied.
	 * @param clazz  the potentially proxied class
	 * @return  the unproxied class
	 */
	static Class<?> unproxy(final Class<?> clazz) {
		Class<?> current = clazz
		while (isProxy(current)) {
			current = current.superclass
		}
		current
	}

	/**
	 * Finds the method in the unproxied superclass if proxied.
	 * @param method  the method
	 * @return  the method in the unproxied class
	 */
	static Method unproxy(final Method method) {
		Class<?> clazz = method.declaringClass

		if (!isProxy(clazz)) {
			return method
		}

		ReflectionUtils.findMethod unproxy(clazz), method.name, method.parameterTypes
	}

	/**
	 * Finds the constructor in the unproxied superclass if proxied.
	 * @param constructor  the constructor
	 * @return  the constructor in the unproxied class
	 */
	static Constructor<?> unproxy(final Constructor<?> constructor) {
		Class<?> clazz = constructor.declaringClass

		if (!isProxy(clazz)) {
			return constructor
		}

		Class<?> searchType = unproxy(clazz)
		while (searchType) {
			for (Constructor<?> c in searchType.constructors) {
				if (constructor.name == c.name
						&& (constructor.parameterTypes == null ||
						    Arrays.equals(constructor.parameterTypes, c.parameterTypes))) {
					return c
				}
			}
			searchType = searchType.superclass
		}
	}

	static boolean isProxy(Class<?> clazz) {
		if (clazz.superclass == Object) {
			return false
		}
	   isCglibProxyClass(clazz) || isJavassistProxy(clazz)
   }

	protected static boolean isJavassistProxy(Class<?> clazz) {
		clazz.interfaces.any { Class<?> c -> c.name.contains('org.hibernate.proxy.HibernateProxy') }
   }

	protected static boolean isCglibProxyClass(Class<?> clazz) {
		ClassUtils.isCglibProxyClass clazz
	}
}
