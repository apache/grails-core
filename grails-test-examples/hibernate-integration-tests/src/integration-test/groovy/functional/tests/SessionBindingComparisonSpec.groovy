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
import grails.gorm.transactions.Rollback
import org.hibernate.SessionFactory
import spock.lang.Specification
import spock.lang.Ignore
import org.springframework.beans.factory.annotation.Autowired

/**
 * Comparison tests showing different session/transaction binding behaviors.
 * This demonstrates the problem described in the issue and how @WithSession solves it.
 */
@Integration
class SessionBindingComparisonSpec extends Specification {
    
    @Autowired
    SessionFactory sessionFactory
    
    // Test 1: Without @Rollback and without @WithSession
    // This test will fail with "No HibernateSession bound to thread"
    @Ignore("Expected to fail - demonstrates the problem")
    void "test without rollback and without session binding fails"() {
        given:
        static transactional = false
        
        when: "Try to perform a SELECT operation"
        TestComparisonDomain.count()
        
        then: "Fails with No HibernateSession bound to thread"
        thrown(IllegalStateException)
    }
    
    // Test 2: With @Rollback - provides both session and transaction
    @Rollback
    void "test with rollback provides session and transaction"() {
        when: "Perform operations"
        def count = TestComparisonDomain.count()
        def domain = new TestComparisonDomain(name: "With Rollback")
        domain.save(flush: true) // This works because transaction is active
        
        then: "Both operations succeed"
        count >= 0
        domain.id != null
        sessionFactory.currentSession != null
        sessionFactory.currentSession.transaction.active
    }
    
    // Test 3: With @WithSession - provides session only, no transaction
    @WithSession
    void "test with session annotation provides session without transaction"() {
        given:
        static transactional = false
        
        when: "Perform SELECT operation"
        def count = TestComparisonDomain.count()
        
        then: "SELECT works with session only"
        count >= 0
        sessionFactory.currentSession != null
        !sessionFactory.currentSession.transaction.active
        
        when: "Try save without flush"
        def domain = new TestComparisonDomain(name: "Session Only")
        domain.save()
        
        then: "Save without flush works"
        domain.id != null
        
        when: "Try save with flush"
        domain.save(flush: true)
        
        then: "Save with flush fails without transaction"
        thrown(Exception)
    }
    
    // Test 4: Method-level @WithSession annotation
    void "test method level session annotation"() {
        given:
        static transactional = false
        
        expect: "This specific test method has session bound"
        withSessionMethod()
    }
    
    @WithSession
    private boolean withSessionMethod() {
        TestComparisonDomain.count() >= 0
        sessionFactory.currentSession != null
        !sessionFactory.currentSession.transaction.active
    }
}

// Test domain class for comparison tests
class TestComparisonDomain {
    String name
    
    static constraints = {
        name nullable: false
    }
}