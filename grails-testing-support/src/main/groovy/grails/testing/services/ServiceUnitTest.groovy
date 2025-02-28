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
package grails.testing.services

import grails.gorm.services.Service
import grails.util.GrailsNameUtils
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.grails.core.artefact.ServiceArtefactHandler
import org.grails.core.exceptions.GrailsConfigurationException
import org.grails.testing.ParameterizedGrailsUnitTest
import org.springframework.util.ClassUtils

@CompileStatic
trait ServiceUnitTest<T> extends ParameterizedGrailsUnitTest<T> {

    public static final String DATATEST_CLASS = 'grails.testing.gorm.DataTest'

    private static Class dataTest
    private static boolean dataTestLoaded

    private void loadDataTestClass() {
        if (!dataTestLoaded) {
            try {
                dataTest = ClassUtils.forName(DATATEST_CLASS)
            } catch (ClassNotFoundException e) {}
        }
        dataTestLoaded = true
    }

    /**
     * Mocks a service class, registering it with the application context
     *
     * @param serviceClass The service class
     * @return An instance of the service
     */
    @CompileDynamic
    void mockArtefact(Class<?> serviceClass) {
        try {
            final serviceArtefact = grailsApplication.addArtefact(ServiceArtefactHandler.TYPE, serviceClass)

            defineBeans {
                "${serviceArtefact.propertyName}"(serviceClass) { bean ->
                    bean.autowire = true
                }
            }
        }
        catch (GrailsConfigurationException e) {
            if (serviceClass.getAnnotation(Service) != null) {
                loadDataTestClass()
                if (dataTest != null && dataTest.isAssignableFrom(this.class)) {
                    dataTest.getMethod('mockDataService', Class).invoke(this, serviceClass)
                }
                else {
                    throw new GrailsConfigurationException("Error attempting to test ${serviceClass.name}. Data services require gorm-testing-support to be on the classpath and the test to implement ${DATATEST_CLASS}")
                }
            }
            else {
                throw e
            }
        }
    }

    String getBeanName(Class<?> serviceClass) {
        GrailsNameUtils.getPropertyName(serviceClass)
    }

    T getService() {
        getArtefactInstance()
    }
}
