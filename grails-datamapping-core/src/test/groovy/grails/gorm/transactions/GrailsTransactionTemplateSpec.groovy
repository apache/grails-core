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
package grails.gorm.transactions

import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus
import spock.lang.Specification

/**
 * The diff here only added guarded debug-log statements around {@code executeAndRollback}'s
 * existing logic - but that whole method (used by {@code @Rollback}'s generated code via
 * {@code RollbackTransform}/{@code DefaultTransactionService}) had 0% coverage of its own
 * functional logic beforehand: the happy-path "always mark rollback-only and return the closure's
 * result" contract, and the "unwrap and rethrow whatever the closure threw" contract, were both
 * entirely untested. {@code execute()} (the sibling method, untouched by this diff) is already
 * heavily, incidentally covered by the rest of the test suite via real {@code @Transactional}
 * usage.
 *
 * Doesn't attempt to force the new debug-log lines themselves - they're guarded by
 * {@code log.isDebugEnabled()}, and this repo has no established pattern for toggling a logger's
 * level from a unit test; not worth introducing one for a handful of pure logging statements.
 */
class GrailsTransactionTemplateSpec extends Specification {

    TransactionStatus status = Mock(TransactionStatus)
    PlatformTransactionManager transactionManager = Mock(PlatformTransactionManager) {
        getTransaction(_) >> status
    }
    GrailsTransactionTemplate template = new GrailsTransactionTemplate(transactionManager)

    void "executeAndRollback returns the closure's result and always marks the transaction rollback-only"() {
        when:
        def result = template.executeAndRollback { TransactionStatus s -> 'the result' }

        then:
        result == 'the result'
        1 * status.setRollbackOnly()
    }

    void "executeAndRollback rethrows a RuntimeException thrown by the closure, still marking rollback-only"() {
        when:
        template.executeAndRollback { throw new IllegalStateException('boom') }

        then:
        def e = thrown(IllegalStateException)
        e.message == 'boom'
        1 * status.setRollbackOnly()
    }

    void "executeAndRollback rethrows a checked exception thrown by the closure unwrapped"() {
        when:
        template.executeAndRollback { throw new java.io.IOException('io boom') }

        then:
        def e = thrown(java.io.IOException)
        e.message == 'io boom'
        1 * status.setRollbackOnly()
    }
}
