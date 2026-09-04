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
package org.apache.grails.core.aot

import java.util.function.Consumer

import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.TypeHint
import org.springframework.aot.hint.TypeReference
import org.springframework.beans.factory.BeanRegistrar
import org.springframework.beans.factory.BeanRegistry
import org.springframework.beans.factory.ListableBeanFactory
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import spock.lang.Specification

/**
 * Covers the registry a plugin declares its beans against being callable from an image.
 *
 * <p>A plugin declares them in a closure, so every call on the registry is made reflectively. An
 * image keeps a method for that only when asked, nothing else asks for these, and the failure is a
 * context that does not start naming an interface the application never mentions.</p>
 */
class BeanRegistrarRuntimeHintsSpec extends Specification {

    RuntimeHints hints = new RuntimeHints()

    void setup() {
        new BeanRegistrarRuntimeHints().registerHints(hints, getClass().classLoader)
    }

    private TypeHint hintFor(Class<?> type) {
        hints.reflection().getTypeHint(TypeReference.of(type))
    }

    void 'the registry a plugin declares its beans against can be called'() {
        expect:
            hintFor(BeanRegistry)?.memberCategories?.contains(MemberCategory.INVOKE_DECLARED_METHODS)
    }

    void 'the specification a plugin configures a bean through can be called'() {
        expect: 'registerBean(name, type) { ... } calls onto it for every bean so declared'
            hintFor(BeanRegistry.Spec)?.memberCategories?.contains(MemberCategory.INVOKE_DECLARED_METHODS)
            hintFor(BeanRegistry.SupplierContext)?.memberCategories?.contains(MemberCategory.INVOKE_DECLARED_METHODS)
    }

    void 'what hands a plugin to the registry can be called'() {
        expect:
            hintFor(BeanRegistrar)?.memberCategories?.contains(MemberCategory.INVOKE_DECLARED_METHODS)
    }

    void 'the registry the older bean DSL is handed can be called'() {
        expect: 'a plugin that still declares its beans that way asks whether one is already there'
            hintFor(BeanDefinitionRegistry)?.memberCategories?.contains(MemberCategory.INVOKE_DECLARED_METHODS)
            hintFor(ListableBeanFactory)?.memberCategories?.contains(MemberCategory.INVOKE_DECLARED_METHODS)
            hintFor(ConfigurableListableBeanFactory)?.memberCategories
                    ?.contains(MemberCategory.INVOKE_DECLARED_METHODS)
    }

    void 'the call a plugin actually makes is covered'() {
        given: 'the three-argument form, which is what a bean with a specification uses'
            def registerBean = BeanRegistry.getMethod('registerBean', String, Class, Consumer)

        expect: 'declared methods covers it, so the image keeps it'
            registerBean.declaringClass == BeanRegistry
            hintFor(BeanRegistry).memberCategories.contains(MemberCategory.INVOKE_DECLARED_METHODS)
    }
}
