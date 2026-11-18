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
package org.grails.datastore.gorm.query.transform

import org.codehaus.groovy.control.MultipleCompilationErrorsException

import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

@RestoreSystemProperties
class GlobalGormQuerySafetyASTTransformationSpec extends Specification {

    private static final String UNSAFE_CLASS_SOURCE = '''
import grails.gorm.annotation.Entity

@Entity
class Book {
    String title

    static List byTitle(String title) {
        String q = "from Book where title = ${title}"
        executeQuery(q)
    }
}
'''

    void "check runs by default with no system property set"() {
        given:
        System.clearProperty(GlobalGormQuerySafetyASTTransformation.PROTECT_SQL_INJECTION_ATTACKS_PROPERTY)

        when:
        new GroovyClassLoader().parseClass(UNSAFE_CLASS_SOURCE)

        then:
        def e = thrown(MultipleCompilationErrorsException)
        e.message.contains('GormUnsafeQueryString')
    }

    void "check runs when protectSqlInjectionAttacks is explicitly true"() {
        given:
        System.setProperty(GlobalGormQuerySafetyASTTransformation.PROTECT_SQL_INJECTION_ATTACKS_PROPERTY, 'true')

        when:
        new GroovyClassLoader().parseClass(UNSAFE_CLASS_SOURCE)

        then:
        def e = thrown(MultipleCompilationErrorsException)
        e.message.contains('GormUnsafeQueryString')
    }

    void "check is skipped entirely when protectSqlInjectionAttacks is false"() {
        given:
        System.setProperty(GlobalGormQuerySafetyASTTransformation.PROTECT_SQL_INJECTION_ATTACKS_PROPERTY, 'false')

        when:
        Class<?> book = new GroovyClassLoader().parseClass(UNSAFE_CLASS_SOURCE)

        then:
        noExceptionThrown()
        book != null
    }
}
