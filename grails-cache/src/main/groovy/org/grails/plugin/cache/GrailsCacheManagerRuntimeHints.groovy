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
package org.grails.plugin.cache

import groovy.transform.CompileStatic

import org.springframework.aot.hint.ExecutableMode
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar
import org.springframework.beans.factory.config.BeanExpressionContext
import org.springframework.beans.factory.support.AbstractBeanFactory
import org.springframework.cache.CacheManager

/**
 * Registers the reflection a native image needs to inject the cache manager of a {@link GrailsCacheManagerAware}
 * class.
 *
 * <p>The trait's field is injected by an expression that reads the bean factory from the expression context and
 * asks it whether the bean {@code grailsCacheManager} exists and is a {@link CacheManager}. The expression
 * language reaches those methods, and the {@code CacheManager} type, by reflection, which a native image
 * permits only for what is registered here.</p>
 *
 * @since 9.0
 */
@CompileStatic
class GrailsCacheManagerRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        hints.reflection()
                .registerMethod(BeanExpressionContext.getMethod('getBeanFactory'), ExecutableMode.INVOKE)
                .registerMethod(AbstractBeanFactory.getMethod('containsBean', String), ExecutableMode.INVOKE)
                .registerMethod(AbstractBeanFactory.getMethod('isTypeMatch', String, Class), ExecutableMode.INVOKE)
                .registerType(CacheManager)
    }
}
