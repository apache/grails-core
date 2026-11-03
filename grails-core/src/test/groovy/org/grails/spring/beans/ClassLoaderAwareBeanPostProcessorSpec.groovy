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
package org.grails.spring.beans

import grails.core.support.ClassLoaderAware
import spock.lang.Specification

class ClassLoaderAwareBeanPostProcessorSpec extends Specification {

    ClassLoader classLoader = new URLClassLoader([] as URL[])
    ClassLoaderAwareBeanPostProcessor processor = new ClassLoaderAwareBeanPostProcessor(classLoader)

    void 'a ClassLoaderAware bean is injected with the configured class loader'() {
        given:
        ClassLoaderFixtureBean bean = new ClassLoaderFixtureBean()

        when:
        Object result = processor.postProcessBeforeInitialization(bean, 'awareBean')

        then:
        result.is(bean)
        bean.classLoader.is(classLoader)
    }

    void 'a plain bean is returned unchanged'() {
        given:
        String bean = 'plain'

        expect:
        processor.postProcessBeforeInitialization(bean, 'plainBean').is(bean)
    }

    void 'postProcessAfterInitialization returns the bean unchanged, inherited from the adapter'() {
        given:
        String bean = 'plain'

        expect:
        processor.postProcessAfterInitialization(bean, 'plainBean').is(bean)
    }

}

class ClassLoaderFixtureBean implements ClassLoaderAware {

    ClassLoader classLoader

    @Override
    void setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader
    }

}
