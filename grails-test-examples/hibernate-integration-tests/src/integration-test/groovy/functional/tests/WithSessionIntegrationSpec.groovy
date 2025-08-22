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
package functional.tests

import grails.testing.mixin.integration.Integration
import grails.testing.mixin.integration.WithSession
import grails.gorm.transactions.Transactional
import org.hibernate.SessionFactory
import spock.lang.Specification
import org.springframework.beans.factory.annotation.Autowired

/**
 * Test demonstrating the @WithSession annotation functionality.
 * This test class shows how to have a Hibernate session bound without a transaction,
 * matching the runtime OISV behavior.
 */
@Integration
@WithSession
class WithSessionIntegrationSpec extends Specification {
    
    @Autowired
    SessionFactory sessionFactory
    
    @Autowired
    TestService testService
    
    // Set transactional to false to prevent automatic transaction wrapping
    static transactional = false
    
    void "test that session is bound without transaction"() {
        given: "A test domain object"
        def testDomain = new TestDomain(name: "Test Session Binding")
        
        when: "We perform a SELECT operation (which requires session but not transaction)"
        def count = TestDomain.count()
        
        then: "The operation succeeds because a session is bound"
        count >= 0
        sessionFactory.currentSession != null
        !sessionFactory.currentSession.transaction.active
    }
    
    void "test that save without flush works with session only"() {
        given: "A domain object"
        def testDomain = new TestDomain(name: "Session Only Save")
        
        when: "We save without flush (no transaction required)"
        testDomain.save()
        
        then: "Save succeeds because session is available"
        testDomain.id != null
        !sessionFactory.currentSession.transaction.active
    }
    
    void "test that save with flush fails without transaction"() {
        given: "A domain object"
        def testDomain = new TestDomain(name: "Flush Test")
        
        when: "We try to save with flush (requires transaction)"
        testDomain.save(flush: true)
        
        then: "An exception is thrown because no transaction is active"
        thrown(Exception)
    }
    
    void "test service method without @Transactional behaves correctly"() {
        when: "Calling a non-transactional service method that does SELECT"
        def result = testService.performNonTransactionalRead()
        
        then: "The method succeeds because session is bound"
        result != null
        !sessionFactory.currentSession.transaction.active
    }
    
    void "test service method with @Transactional creates transaction"() {
        when: "Calling a transactional service method"
        def result = testService.performTransactionalOperation()
        
        then: "The method runs in a transaction"
        result != null
        // Transaction will be committed after method completes
    }
}

// Test domain class
class TestDomain {
    String name
    
    static constraints = {
        name nullable: false
    }
}

// Test service class
@grails.gorm.services.Service(TestDomain)
abstract class TestService {
    
    // Non-transactional method - relies on session from OISV
    def performNonTransactionalRead() {
        return TestDomain.count()
    }
    
    // Transactional method - creates its own transaction
    @Transactional
    def performTransactionalOperation() {
        def domain = new TestDomain(name: "Transactional Save")
        domain.save(flush: true)
        return domain
    }
}