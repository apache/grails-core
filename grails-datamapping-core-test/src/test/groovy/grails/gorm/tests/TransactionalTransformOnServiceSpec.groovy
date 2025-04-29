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
package grails.gorm.tests

import org.apache.grails.data.testing.tck.domains.Person
import grails.gorm.transactions.Transactional
import org.apache.grails.data.simple.core.GrailsDataCoreTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec

/**
 * Created by graemerocher on 05/01/2017.
 */
class TransactionalTransformOnServiceSpec extends GrailsDataTckSpec<GrailsDataCoreTckManager> {
    void setupSpec() {
        manager.domainClasses.addAll([Person])
    }

    void "test transaction manager lookup with @Transactional and unassigned transaction manager"() {
        expect:
        new TestService().testMe()
        new ChildService().doSomething("value") == "parent value"
    }


}
@Transactional
class TestService {

    boolean testMe() {
        return transactionStatus != null
    }
}
abstract class ParentService {

    @Transactional
    String doSomething(String arg) {
        "parent $arg"
    }
}
class ChildService extends ParentService {


    @Transactional
    String doSomething(String arg) {
        super.doSomething(arg)
    }
}