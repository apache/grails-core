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
package grails.gorm.tests.validation

import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import grails.validation.ValidationException
import org.grails.orm.hibernate.HibernateDatastore
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

class EmbeddedWithValidationExceptionSpec extends Specification {
    @Shared @AutoCleanup HibernateDatastore hibernateDatastore = new HibernateDatastore(DomainWithEmbedded)

    @Rollback
    @Issue("https://github.com/grails/gorm-hibernate5/issues/110")
    void "test validation exception with embedded in domain"() {
        when:
        new DomainWithEmbedded(
                foo: 'not valid',
                myEmbedded: new MyEmbedded(
                        a: 1,
                        b: 'foo'
                )
        ).save(failOnError: true)

        then:
        thrown(ValidationException)
    }
}

@Entity
class DomainWithEmbedded {
    MyEmbedded myEmbedded
    String foo

    static embedded = ['myEmbedded']

    static constraints = {
        foo(validator: { val, self ->
            return 'not.valid.foo'
        })
    }
}

class MyEmbedded {
    Integer a
    String b

    static constraints = {
        a(nullable: true)
        b(nullalbe: true)
    }
}