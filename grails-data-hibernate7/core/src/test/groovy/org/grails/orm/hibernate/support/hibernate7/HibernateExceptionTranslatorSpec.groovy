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
package org.grails.orm.hibernate.support.hibernate7

import org.hibernate.HibernateException
import org.hibernate.exception.ConstraintViolationException
import org.springframework.dao.DataAccessException
import spock.lang.Specification
import java.sql.SQLException

class HibernateExceptionTranslatorSpec extends Specification {

    def "test translateExceptionIfPossible translates Hibernate exceptions"() {
        given: "A translator and a Hibernate exception"
        def translator = new HibernateExceptionTranslator()
        def hibernateEx = new HibernateException("Test exception")

        when: "translateExceptionIfPossible is called"
        DataAccessException dae = translator.translateExceptionIfPossible(hibernateEx)

        then: "it is translated to a Spring DataAccessException"
        dae != null
        dae.message.contains("Test exception")

        when: "a ConstraintViolationException is translated"
        def cve = new ConstraintViolationException("Violation", new SQLException("SQL error"), "UK_TEST")
        dae = translator.translateExceptionIfPossible(cve)

        then: "it is correctly translated"
        dae != null
    }
}
