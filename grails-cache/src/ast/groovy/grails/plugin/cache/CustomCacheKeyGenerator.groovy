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
package grails.plugin.cache

import groovy.transform.CompileStatic
import org.springframework.aop.framework.AopProxyUtils
import org.springframework.cache.interceptor.KeyGenerator
import org.springframework.cache.interceptor.SimpleKeyGenerator

import java.lang.reflect.Method

/**
 * Includes the hashcode, method signature, and class name of the target (caller) in the cache key
 */
@CompileStatic
class CustomCacheKeyGenerator implements KeyGenerator, GrailsCacheKeyGenerator {
	
	private final KeyGenerator innerKeyGenerator

	CustomCacheKeyGenerator(KeyGenerator innerKeyGenerator){
		this.innerKeyGenerator = innerKeyGenerator
	}

	CustomCacheKeyGenerator(){
		this.innerKeyGenerator = new SimpleKeyGenerator()
	}

	@SuppressWarnings("serial")
	private static final class CacheKey implements Serializable {
		final String targetClassName
		final String targetMethodName
		final int targetObjectHashCode
		final Object simpleKey

		CacheKey(String targetClassName, String targetMethodName,
				 int targetObjectHashCode, Object simpleKey) {
			this.targetClassName = targetClassName
			this.targetMethodName = targetMethodName
			this.targetObjectHashCode = targetObjectHashCode
			this.simpleKey = simpleKey
		}
		@Override
		int hashCode() {
			final int prime = 31
			int result = 1
			result = prime * result
					+ ((simpleKey == null) ? 0 : simpleKey.hashCode())
			result = prime * result
					+ ((targetClassName == null) ? 0 : targetClassName
							.hashCode())
			result = prime * result
					+ ((targetMethodName == null) ? 0 : targetMethodName
							.hashCode())
			result = prime * result + targetObjectHashCode
			return result
		}
		@Override
		boolean equals(Object obj) {
			if (this.is(obj))
				return true
			if (obj == null)
				return false
			if (getClass() != obj.getClass())
				return false
			CacheKey other = (CacheKey) obj
			if (simpleKey == null) {
				if (other.simpleKey != null)
					return false
			} else if (!simpleKey.equals(other.simpleKey))
				return false
			else if ( simpleKey.equals(other.simpleKey) && !(simpleKey instanceof Map && ((Map)simpleKey).size() == 0 ) ) {
				return true // equal if simpleKey is identical but not an empty map
			}

			if (targetClassName == null) {
				if (other.targetClassName != null)
					return false
			} else if (!targetClassName.equals(other.targetClassName))
				return false
			if (targetMethodName == null) {
				if (other.targetMethodName != null)
					return false
			} else if (!targetMethodName.equals(other.targetMethodName))
				return false
			if (targetObjectHashCode != other.targetObjectHashCode)
				return false
			return true
		}
	}

	Object generate(Object target, Method method, Object... params) {
		Class<?> objClass = AopProxyUtils.ultimateTargetClass(target)

		return new CacheKey(
				objClass.getName().intern(),
				method.toString().intern(),
				target.hashCode(), innerKeyGenerator.generate(target, method, params))
	}

	@Override
	Serializable generate(String className, String methodName, int objHashCode, Closure keyGenerator) {
		final Object simpleKey = keyGenerator.call()
		return new TemporaryGrailsCacheKey(className, methodName, objHashCode, simpleKey)
	}

	@Override
	Serializable generate(String className, String methodName, int objHashCode, Map methodParams) {
		final Object simpleKey = methodParams
		return new TemporaryGrailsCacheKey(className, methodName, objHashCode, simpleKey)
	}

	@CompileStatic
	private static class TemporaryGrailsCacheKey implements Serializable {
		final String targetClassName
		final String targetMethodName
		final int targetObjectHashCode
		final Object simpleKey

		TemporaryGrailsCacheKey(String targetClassName, String targetMethodName,
								int targetObjectHashCode, Object simpleKey) {
			this.targetClassName = targetClassName
			this.targetMethodName = targetMethodName
			this.targetObjectHashCode = targetObjectHashCode
			this.simpleKey = simpleKey
		}
		@Override
		int hashCode() {
			final int prime = 31
			int result = 1
			result = prime * result
			+ ((simpleKey == null) ? 0 : simpleKey.hashCode())
			result = prime * result
			+ ((targetClassName == null) ? 0 : targetClassName
					.hashCode())
			result = prime * result
			+ ((targetMethodName == null) ? 0 : targetMethodName
					.hashCode())
			result = prime * result + targetObjectHashCode
			return result
		}
		@Override
		boolean equals(Object obj) {
			if (this.is(obj))
				return true
			if (obj == null)
				return false
			if (getClass() != obj.getClass())
				return false
			TemporaryGrailsCacheKey other = (TemporaryGrailsCacheKey) obj
			if (simpleKey == null) {
				if (other.simpleKey != null)
					return false
			} else if (!simpleKey.equals(other.simpleKey))
				return false
			else if ( simpleKey.equals(other.simpleKey) && !(simpleKey instanceof Map && ((Map)simpleKey).size() == 0 ) ) {
				return true // equal if simpleKey is identical but not an empty map
			}

			if (targetClassName == null) {
				if (other.targetClassName != null)
					return false
			} else if (!targetClassName.equals(other.targetClassName))
				return false
			if (targetMethodName == null) {
				if (other.targetMethodName != null)
					return false
			} else if (!targetMethodName.equals(other.targetMethodName))
				return false
			if (targetObjectHashCode != other.targetObjectHashCode)
				return false
			return true
		}
	}

}
