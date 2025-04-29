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
package grails.gorm.annotation.multitenancy

import spock.lang.Specification

/**
 * Created by graemerocher on 16/01/2017.
 */
class CurrentTenantTransformSpec extends Specification {

    void "test @CurrentTenant transforms a service and makes a method that is wrapped in current tenant handling"() {
        given:"A service with @CurrentTenant applied as the class level"
        def bookService = new GroovyShell().evaluate('''
import grails.gorm.multitenancy.CurrentTenant
class BookService {
    @CurrentTenant
    List listBooks() {
        return ["The Stand"]
    }
}
new BookService()

''')
        when:"the list books method is invoked"
        def result = bookService.listBooks()

        then:"An exception was thrown because GORM is not setup"
        thrown(IllegalStateException)

    }

    void "test @CurrentTenant transforms a service class and makes a method in current tenant handling"() {
        given:"A service with @CurrentTenant applied as the class level"
        def bookService = new GroovyShell().evaluate('''
import grails.gorm.multitenancy.CurrentTenant

@CurrentTenant
class BookService {
   
    List listBooks() {
        return ["The Stand"]
    }
}
new BookService()

''')
        when:"the list books method is invoked"
        def result = bookService.listBooks()

        then:"An exception was thrown because GORM is not setup"
        thrown(IllegalStateException)

    }

    void "test @CurrentTenant transforms a service class and a method marked with @WithoutTenant in no tenant handling"() {
        given:"A service with @CurrentTenant applied as the class level"
        def bookService = new GroovyShell().evaluate('''
import grails.gorm.multitenancy.CurrentTenant
import grails.gorm.multitenancy.WithoutTenant

@CurrentTenant
class BookService {
   
   @WithoutTenant
    List listBooks() {
        return ["The Stand"]
    }
}
new BookService()

''')
        when:"the list books method is invoked"
        def result = bookService.listBooks()

        then:"An exception was thrown because GORM is not setup"
        thrown(IllegalStateException)

    }

    void "test @WithoutTenant transforms a service class and makes a method that is wrapped in without tenant handling"() {
        given:"A service with @CurrentTenant applied as the class level"
        def bookService = new GroovyShell().evaluate('''
import grails.gorm.multitenancy.CurrentTenant
import grails.gorm.multitenancy.WithoutTenant

@WithoutTenant
class BookService {
   
    List listBooks() {
        return ["The Stand"]
    }
}
new BookService()

''')
        when:"the list books method is invoked"
        def result = bookService.listBooks()

        then:"An exception was thrown because GORM is not setup"
        thrown(IllegalStateException)

    }

    void "test @WithoutTenant transforms a service class and a method marked with @CurrentTenant in current tenant handling"() {
        given:"A service with @CurrentTenant applied as the class level"
        def bookService = new GroovyShell().evaluate('''
import grails.gorm.multitenancy.CurrentTenant
import grails.gorm.multitenancy.WithoutTenant

@WithoutTenant
class BookService {
   
    @CurrentTenant
    List listBooks() {
        return ["The Stand"]
    }
}
new BookService()

''')
        when:"the list books method is invoked"
        def result = bookService.listBooks()

        then:"An exception was thrown because GORM is not setup"
        thrown(IllegalStateException)

    }
}
