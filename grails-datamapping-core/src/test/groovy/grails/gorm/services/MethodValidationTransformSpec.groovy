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
package grails.gorm.services

import java.lang.reflect.Method

import groovy.transform.Generated

import jakarta.validation.ConstraintViolationException
import jakarta.validation.ParameterNameProvider
import jakarta.validation.constraints.NotNull

import spock.lang.Specification

import grails.gorm.annotation.Entity
import grails.validation.ValidationException
import org.grails.datastore.gorm.validation.jakarta.services.ValidatedService

class MethodValidationTransformSpec extends Specification {

    void 'test simple validated property'() {

        when: 'the service transform is applied to an interface it cannot implement'
        def serviceClass = new GroovyClassLoader().parseClass('''
            import grails.gorm.services.*
            import grails.gorm.annotation.Entity
            import jakarta.validation.constraints.*
            
            @Service(Foo)
            interface MyService {
            
                Foo find(@NotNull String title) throws jakarta.validation.ConstraintViolationException
                
                Foo findAgain(@NotNull @NotBlank String title)
            }
            @Entity
            class Foo {
                String title
            }
        ''')

        then:
        serviceClass.isInterface()

        when: 'the impl is obtained'
        def implClass = serviceClass.classLoader.loadClass('$MyServiceImplementation')

        then: 'the impl is valid'
        org.grails.datastore.mapping.services.Service.isAssignableFrom(implClass)
        ValidatedService.isAssignableFrom(implClass)

        and: 'all implemented Trait methods are marked as Generated'
        ValidatedService.declaredMethods.findAll { !it.synthetic && !it.name.contains('$') }.each { Method traitMethod ->
            assert implClass.getMethod(traitMethod.name, traitMethod.parameterTypes).isAnnotationPresent(Generated)
        }

        when: 'the parameter data is obtained'
        def fooClass = serviceClass.classLoader.loadClass('Foo')
        def datastore = new org.grails.datastore.mapping.simple.SimpleMapDatastore(fooClass)
        datastore.mappingContext.setValidatorRegistry(new org.grails.datastore.gorm.validation.constraints.registry.DefaultValidatorRegistry(datastore.mappingContext, datastore.connectionSources.defaultConnectionSource.settings))
        def parameterNameProvider = (ParameterNameProvider) serviceClass.classLoader.loadClass('$MyServiceImplementation$ParameterNameProvider').newInstance()
        def instance = implClass.newInstance()
        instance.setDatastore(datastore)

        then: 'it is correct'
        parameterNameProvider != null
        parameterNameProvider.getParameterNames(implClass.getMethod('find', String)) == ['title']
        instance.parameterNameProvider != null
        instance.parameterNameProvider.getParameterNames(implClass.getMethod('find', String)) == ['title']
        instance.validatorFactory != null

        when:
        instance.find(null)

        then:
        def constraintViolationException = thrown(ConstraintViolationException)
        constraintViolationException.constraintViolations.size() == 1
        constraintViolationException.constraintViolations.first().messageTemplate == '{jakarta.validation.constraints.NotNull.message}'
        constraintViolationException.constraintViolations.first().propertyPath.toString() == 'find.title'

        when:
        instance.findAgain('')

        then:
        def validationException =  thrown(ValidationException)
        validationException.message
        validationException.errors.hasErrors()
        validationException.errors.hasFieldErrors('title')
        validationException.errors.getFieldValue('title') == ''
    }
}

@Service(Foo)
interface MyService {
    Foo find(@NotNull String title)
}

@Entity
class Foo {
    String title
}
