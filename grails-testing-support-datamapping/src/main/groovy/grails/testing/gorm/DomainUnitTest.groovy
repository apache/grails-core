/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package grails.testing.gorm

import groovy.transform.CompileStatic

import java.lang.reflect.ParameterizedType

@CompileStatic
trait DomainUnitTest<T> implements DataTest {

    private T domainInstance
    private static Class<T> domainClass

    Class<?>[] getDomainClassesToMock() {
        [getDomainUnderTest()].toArray(Class)
    }

    /**
     * @return An instance of the domain class
     */
    T getDomain() {
        if (domainInstance == null) {
            domainInstance = getDomainUnderTest().newInstance()
        }
        domainInstance
    }

    private Class<T> getDomainUnderTest() {
        if (domainClass == null) {
            ParameterizedType parameterizedType = (ParameterizedType)getClass().genericInterfaces.find { genericInterface ->
                genericInterface instanceof ParameterizedType &&
                        DomainUnitTest.isAssignableFrom((Class)((ParameterizedType)genericInterface).rawType)
            }

            domainClass = (Class<T>)parameterizedType?.actualTypeArguments[0]
        }
        domainClass
    }

}
