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

import grails.gorm.annotation.Entity
import grails.validation.ValidationException
import jakarta.validation.constraints.NotNull
import org.grails.datastore.gorm.validation.jakarta.services.ValidatedService
import spock.lang.Specification

import jakarta.validation.ConstraintViolationException
import jakarta.validation.ParameterNameProvider

/**
 * Created by graemerocher on 14/02/2017.
 */
class MethodValidationTransformSpec extends Specification {

    void "test simple validated property"() {
        when:"The service transform is applied to an interface it can't implement"
        Class service = new GroovyClassLoader().parseClass('''
import grails.gorm.services.*
import grails.gorm.annotation.Entity
import jakarta.validation.constraints.*

@Service(Foo)
interface MyService {

    @grails.gorm.transactions.NotTransactional
    Foo find(@NotNull String title) throws jakarta.validation.ConstraintViolationException
    
    @grails.gorm.transactions.NotTransactional
    Foo findAgain(@NotNull @NotBlank String title)
}
@Entity
class Foo {
    String title
}

''')

        then:
        service.isInterface()
        println service.classLoader.loadedClasses

        when:"the impl is obtained"
        Class impl = service.classLoader.loadClass("\$MyServiceImplementation")

        then:"The impl is valid"
        org.grails.datastore.mapping.services.Service.isAssignableFrom(impl)
        ValidatedService.isAssignableFrom(impl)

        when:"The parameter data is obtained"
        ParameterNameProvider parameterNameProvider = service.classLoader.loadClass("\$MyServiceImplementation\$ParameterNameProvider").newInstance()
        def instance = impl.newInstance()

        then:"It is correct"
        parameterNameProvider != null
        parameterNameProvider.getParameterNames(impl.getMethod("find", String)) == ["title"]
        instance.parameterNameProvider != null
        instance.parameterNameProvider.getParameterNames(impl.getMethod("find", String)) == ["title"]
        instance.validatorFactory != null


        when:
        instance.find(null)

        then:
        def e = thrown( ConstraintViolationException)
        e.constraintViolations.size() == 1
        e.constraintViolations.first().messageTemplate == '{jakarta.validation.constraints.NotNull.message}'
        e.constraintViolations.first().propertyPath.toString() == 'find.title'

        when:
        instance.findAgain("")

        then:
        def e2 = thrown( ValidationException )
        e2.message
        e2.errors.hasErrors()
        e2.errors.hasFieldErrors('title')
        e2.errors.getFieldValue('title') == ""
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
