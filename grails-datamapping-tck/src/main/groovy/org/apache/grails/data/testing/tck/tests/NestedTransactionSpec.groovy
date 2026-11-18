/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.grails.data.testing.tck.tests

import org.springframework.transaction.TransactionDefinition

import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.apache.grails.data.testing.tck.domains.ChildEntity
import org.apache.grails.data.testing.tck.domains.TestEntity

/**
 * A transaction started inside another: what every datastore does with it. Each feature runs on a fresh
 * thread, as {@link WithTransactionSpec}'s do, so the harness's thread-bound session is not the one the
 * transactions begin on.
 */
class NestedTransactionSpec extends GrailsDataTckSpec {

    @Override
    void setupSpec() {
        manager.registerDomainClasses(TestEntity, ChildEntity)
    }

    void 'a transaction started inside another joins it, and every write of both commits'() {
        when:
        Throwable failure = onFreshThread {
            TestEntity.withTransaction {
                entity('A').save()
                TestEntity.withTransaction {
                    entity('B').save()
                }
                entity('C').save()
            }
        }

        then:
        failure == null
        names() == ['A', 'B', 'C']
    }

    void 'a joined transaction that fails rolls back the whole transaction'() {
        when:
        Throwable failure = onFreshThread {
            TestEntity.withTransaction {
                entity('A').save()
                TestEntity.withTransaction {
                    entity('B').save()
                    throw new RuntimeException('inner')
                }
            }
        }

        then:
        failure instanceof RuntimeException
        names().empty
    }

    void 'a transaction that catches a joined transaction\'s failure is rolled back as a whole'() {
        when:
        Throwable failure = onFreshThread {
            TestEntity.withTransaction {
                entity('A').save()
                try {
                    TestEntity.withTransaction {
                        entity('B').save()
                        throw new IllegalStateException('inner')
                    }
                }
                catch (IllegalStateException ignored) {
                }
                entity('C').save()
            }
        }

        then: 'the failed participant marked it rollback-only'
        failure == null
        names().empty
    }

    void 'a REQUIRES_NEW transaction commits on its own when the surrounding one rolls back'() {
        when:
        Throwable failure = onFreshThread {
            TestEntity.withTransaction {
                entity('outer').save()
                TestEntity.withTransaction([propagationBehavior: TransactionDefinition.PROPAGATION_REQUIRES_NEW]) {
                    entity('inner').save()
                }
                throw new RuntimeException('outer')
            }
        }

        then:
        failure instanceof RuntimeException
        names() == ['inner']
    }

    void 'a transaction in a new session inside a transaction is its own, and commits its writes'() {
        when:
        Throwable failure = onFreshThread {
            TestEntity.withTransaction {
                entity('A').save()
                TestEntity.withNewSession {
                    TestEntity.withTransaction {
                        entity('B').save()
                    }
                }
                entity('C').save()
            }
        }

        then:
        failure == null
        names() == ['A', 'B', 'C']
    }

    void 'a transaction in a new session, run after a joined transaction failed, commits its writes and the outer one still rolls back'() {
        when:
        Throwable failure = onFreshThread {
            TestEntity.withTransaction {
                entity('A').save()
                try {
                    TestEntity.withTransaction {
                        entity('B').save()
                        throw new IllegalStateException('inner')
                    }
                }
                catch (IllegalStateException ignored) {
                }
                TestEntity.withNewSession {
                    TestEntity.withTransaction {
                        entity('audit').save()
                    }
                }
                entity('C').save()
            }
        }

        then: 'the failure is recorded in a transaction of its own, which neither inherits nor clears the outer one\'s rollback-only mark'
        failure == null
        names() == ['audit']
    }

    private static TestEntity entity(String name) {
        new TestEntity(name: name, age: 30, child: new ChildEntity(name: "${name} child"))
    }

    private static List<String> names() {
        TestEntity.list()*.name.sort()
    }

    private static Throwable onFreshThread(Closure<?> work) {
        Throwable failure = null
        Thread.start {
            try {
                work()
            }
            catch (Throwable t) {
                failure = t
            }
        }.join()
        failure
    }
}
