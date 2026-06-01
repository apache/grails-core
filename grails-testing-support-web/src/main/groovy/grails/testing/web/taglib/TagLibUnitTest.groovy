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
package grails.testing.web.taglib

import java.lang.reflect.ParameterizedType

import groovy.transform.CompileStatic

import grails.core.gsp.GrailsTagLibClass
import grails.testing.web.GrailsWebUnitTest
import org.grails.testing.ParameterizedGrailsUnitTest
import org.grails.taglib.TagLibraryLookup

@CompileStatic
trait TagLibUnitTest<T> implements ParameterizedGrailsUnitTest<T>, GrailsWebUnitTest {

    private static final Map<Class<?>, Set<Class<?>>> MOCKED_TAG_LIB_CLASSES_BY_SPEC = [:].withDefault { [] as LinkedHashSet<Class<?>> }
    private boolean hasBeenMocked = false

    /**
     * Renders a template for the given contents and model
     *
     * @param contents The contents
     * @param model The model
     * @return The rendered template
     */
    String applyTemplate(String contents, Map model = [:]) {
        ensureTaglibHasBeenMocked()
        super.applyTemplate(contents, model)
    }

    void applyTemplate(StringWriter sw, String template, Map params = [:]) {
        ensureTaglibHasBeenMocked()
        super.applyTemplate(sw, template, params)
    }

    /**
     * Mocks a tag library, making it available to subsequent calls to controllers mocked via
     * {@link #mockArtefact(Class) } and GSPs rendered via {@link #applyTemplate(String, Map) }
     *
     * @param tagLibClass The tag library class
     * @return The tag library instance
     */
    void mockArtefact(Class<?> tagLibClass) {
        mockTagLib(tagLibClass)
    }

    Object mockTagLib(Class<?> tagLibClass) {
        getMockedTagLibClasses().add(tagLibClass)
        GrailsWebUnitTest.super.mockTagLib(tagLibClass)
    }

    void mockTagLibs(Class<?>... tagLibClasses) {
        for (Class<?> tagLibClass in tagLibClasses) {
            mockTagLib(tagLibClass)
        }
    }

    String getBeanName(Class<?> tagLibClass) {
        tagLibClass.name
    }

    private Class<T> getTagLibTypeUnderTest() {
        ParameterizedType parameterizedType = (ParameterizedType) getClass().genericInterfaces.find { genericInterface ->
            genericInterface instanceof ParameterizedType &&
                    TagLibUnitTest.isAssignableFrom((Class)((ParameterizedType)genericInterface).rawType)
        }

        parameterizedType?.actualTypeArguments[0]
    }

    T getTagLib() {
        ensureTaglibHasBeenMocked()
        getArtefactInstance()
    }

    private void ensureTaglibHasBeenMocked() {
        if (!hasBeenMocked || !areMockedTagLibsRegistered()) {
            Set<Class<?>> mockedTagLibClasses = getMockedTagLibClasses()
            Class<?> tagLibTypeUnderTest = getTagLibTypeUnderTest()
            if (tagLibTypeUnderTest != null) {
                mockedTagLibClasses.add(tagLibTypeUnderTest)
            }
            for (Class<?> tagLibClass in mockedTagLibClasses) {
                GrailsWebUnitTest.super.mockTagLib(tagLibClass)
            }
            hasBeenMocked = true
        }
    }

    private boolean areMockedTagLibsRegistered() {
        TagLibraryLookup tagLibraryLookup = applicationContext.getBean(TagLibraryLookup)
        for (Class<?> tagLibClass in getMockedTagLibClasses()) {
            GrailsTagLibClass grailsTagLibClass = (GrailsTagLibClass) grailsApplication.getArtefact('TagLib', tagLibClass.name)
            if (grailsTagLibClass == null) {
                return false
            }
            String namespace = grailsTagLibClass.namespace
            if (!grailsTagLibClass.tagNames.every { String tagName ->
                tagLibraryLookup.lookupTagLibrary(namespace, tagName) != null
            }) {
                return false
            }
        }
        return true
    }

    private Set<Class<?>> getMockedTagLibClasses() {
        MOCKED_TAG_LIB_CLASSES_BY_SPEC.get(getClass())
    }
}
