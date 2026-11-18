/*
 * Copyright (c) 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package grails.plugins.quartz

import spock.lang.Specification

class DefaultGrailsJobClassSpec extends Specification {
    protected GroovyClassLoader gcl = new GroovyClassLoader()

    def cleanup() {
        gcl.clearCache()
    }

    void 'default properties are set correctly'() {
        setup:
            def jobClass = gcl.parseClass('class TestJob { def execute(){} }')
            def grailsJobClass = new DefaultGrailsJobClass(jobClass)
        expect:
            assert 'GRAILS_JOBS' == grailsJobClass.group: "Wrong default group"
            assert grailsJobClass.sessionRequired: "Job should require Hibernate session by default"
            assert grailsJobClass.concurrent: "Job should be concurrent by default"
    }

    void 'job class execute method works correctly'() {
        setup:
            boolean wasExecuted = false
            def testClosure = { wasExecuted = true }
            Class jobClass = gcl.parseClass("""
                class TestJob {
                    static testClosure
                    def execute() {
                        testClosure.call()
                    }
                }
                """.stripIndent())
            GrailsJobClass grailsJobClass = new DefaultGrailsJobClass(jobClass)
            grailsJobClass.referenceInstance.testClosure = testClosure
        when:
            grailsJobClass.execute()
        then:
            assert wasExecuted: "Job wasn't executed"
    }

    void 'session required parameter is handled correctly'() {
        setup:
            Class jobClass = gcl.parseClass("""
                class TestJob {
                    static sessionRequired = false
                    def execute() {}
                }
                """.stripIndent())
            GrailsJobClass grailsJobClass = new DefaultGrailsJobClass(jobClass)
        expect:
            assert !grailsJobClass.sessionRequired: "Hibernate Session shouldn't be required"
    }

    void 'concurrent parameter is handled correctly'() {
        setup:
            Class jobClass = gcl.parseClass("""
                class TestJob {
                    static concurrent = false
                    def execute() {}
                }
                """.stripIndent())
            GrailsJobClass grailsJobClass = new DefaultGrailsJobClass(jobClass)
        expect:
            assert !grailsJobClass.concurrent: "Job class shouldn't be marked as concurrent"
    }

    void 'group parameter is handled correctly'() {
        setup:
            Class jobClass = gcl.parseClass("""
                class TestJob {
                    static group = 'myGroup'
                    def execute() {}
                }
                """.stripIndent())
            GrailsJobClass grailsJobClass = new DefaultGrailsJobClass(jobClass)
        expect:
            'myGroup' == grailsJobClass.group
    }
}
