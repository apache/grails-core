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
package org.grails.plugin.cache.aot

import java.lang.reflect.Field

import groovy.transform.CompileStatic

import org.springframework.aot.generate.GenerationContext
import org.springframework.beans.factory.aot.BeanRegistrationAotContribution
import org.springframework.beans.factory.aot.BeanRegistrationAotProcessor
import org.springframework.beans.factory.aot.BeanRegistrationCode
import org.springframework.beans.factory.support.RegisteredBean

import org.grails.plugin.cache.GrailsCacheManagerFallback

/**
 * Registers the trait's cache manager field of each cache-aware bean for reflection, as the field is set by
 * {@link GrailsCacheManagerFallback} where Spring's own injection, which the code generated ahead of time
 * performs, leaves it.
 *
 * @since 8.0.1
 */
@CompileStatic
class GrailsCacheManagerFallbackAotProcessor implements BeanRegistrationAotProcessor {

    @Override
    BeanRegistrationAotContribution processAheadOfTime(RegisteredBean registeredBean) {
        Field field = GrailsCacheManagerFallback.traitField(registeredBean.beanClass)
        field == null ? null : new FieldContribution(field)
    }

    @CompileStatic
    private static class FieldContribution implements BeanRegistrationAotContribution {

        private final Field field

        FieldContribution(Field field) {
            this.field = field
        }

        @Override
        void applyTo(GenerationContext generationContext, BeanRegistrationCode beanRegistrationCode) {
            generationContext.runtimeHints.reflection().registerField(field)
        }
    }
}
