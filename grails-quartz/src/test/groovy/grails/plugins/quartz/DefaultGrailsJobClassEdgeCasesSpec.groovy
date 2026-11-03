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
package grails.plugins.quartz

import org.quartz.JobExecutionContext
import spock.lang.Specification

class DefaultGrailsJobClassEdgeCasesSpec extends Specification {

    protected GroovyClassLoader gcl = new GroovyClassLoader()

    def cleanup() {
        gcl.clearCache()
    }

    void 'execute with a JobExecutionContext argument passes it through to the job artefact'() {
        given:
        JobExecutionContext context = Stub(JobExecutionContext)
        Class jobClass = gcl.parseClass('''
            class TestJob {
                static seen
                def execute(context) { seen = context }
            }
        ''')
        DefaultGrailsJobClass grailsJobClass = new DefaultGrailsJobClass(jobClass)

        when:
        grailsJobClass.execute(context)

        then:
        jobClass.seen.is(context)
    }

    void 'durability, requestsRecovery and enabled default true/true/true unless overridden'() {
        given:
        Class defaultJob = gcl.parseClass('class DgcDefaultJob { def execute(){} }')
        Class overriddenJob = gcl.parseClass('''
            class DgcOverriddenJob {
                static durability = false
                static requestsRecovery = true
                static jobEnabled = false
                def execute(){}
            }
        ''')

        expect:
        new DefaultGrailsJobClass(defaultJob).durability
        !new DefaultGrailsJobClass(defaultJob).requestsRecovery
        new DefaultGrailsJobClass(defaultJob).enabled
        !new DefaultGrailsJobClass(overriddenJob).durability
        new DefaultGrailsJobClass(overriddenJob).requestsRecovery
        !new DefaultGrailsJobClass(overriddenJob).enabled
    }

    void 'a missing or blank description falls back to the default'() {
        given:
        Class missing = gcl.parseClass('class DgcMissingDescJob { def execute(){} }')
        Class blank = gcl.parseClass('''
            class DgcBlankDescJob {
                static description = ''
                def execute(){}
            }
        ''')
        Class present = gcl.parseClass('''
            class DgcPresentDescJob {
                static description = 'does a thing'
                def execute(){}
            }
        ''')

        expect:
        new DefaultGrailsJobClass(missing).description == 'Grails Job'
        new DefaultGrailsJobClass(blank).description == 'Grails Job'
        new DefaultGrailsJobClass(present).description == 'does a thing'
    }

    void 'getTriggers builds from the triggers closure once and caches the result'() {
        given:
        Class jobClass = gcl.parseClass('''
            class DgcTriggeredJob {
                static triggers = {
                    simple name: 'every5', repeatInterval: 5000l
                }
                def execute(){}
            }
        ''')
        DefaultGrailsJobClass grailsJobClass = new DefaultGrailsJobClass(jobClass)

        when:
        Map first = grailsJobClass.triggers

        then:
        first.containsKey('every5')

        when:
        Map second = grailsJobClass.triggers

        then:
        second.is(first)
    }

    void 'a job with no triggers closure has an empty triggers map'() {
        given:
        Class jobClass = gcl.parseClass('class DgcNoTriggersJob { def execute(){} }')

        expect:
        new DefaultGrailsJobClass(jobClass).triggers.isEmpty()
    }

}
