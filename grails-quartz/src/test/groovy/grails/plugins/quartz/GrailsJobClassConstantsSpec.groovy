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

import org.quartz.SimpleTrigger
import spock.lang.Specification

class GrailsJobClassConstantsSpec extends Specification {

    void 'the property name constants match the historical job DSL keys'() {
        expect:
        GrailsJobClassConstants.EXECUTE == 'execute'
        GrailsJobClassConstants.INTERRUPT == 'interrupt'
        GrailsJobClassConstants.START_DELAY == 'startDelay'
        GrailsJobClassConstants.CRON_EXPRESSION == 'cronExpression'
        GrailsJobClassConstants.NAME == 'name'
        GrailsJobClassConstants.GROUP == 'group'
        GrailsJobClassConstants.DESCRIPTION == 'description'
        GrailsJobClassConstants.CONCURRENT == 'concurrent'
        GrailsJobClassConstants.SESSION_REQUIRED == 'sessionRequired'
        GrailsJobClassConstants.TIMEOUT == 'timeout'
        GrailsJobClassConstants.REPEAT_INTERVAL == 'repeatInterval'
        GrailsJobClassConstants.REPEAT_COUNT == 'repeatCount'
        GrailsJobClassConstants.DURABILITY == 'durability'
        GrailsJobClassConstants.REQUESTS_RECOVERY == 'requestsRecovery'
        GrailsJobClassConstants.ENABLED == 'jobEnabled'
    }

    void 'the default value constants match the historical scheduling defaults'() {
        expect:
        GrailsJobClassConstants.DEFAULT_REPEAT_INTERVAL == 60000L
        GrailsJobClassConstants.DEFAULT_START_DELAY == 0L
        GrailsJobClassConstants.DEFAULT_REPEAT_COUNT == SimpleTrigger.REPEAT_INDEFINITELY
        GrailsJobClassConstants.DEFAULT_CRON_EXPRESSION == '0 0 6 * * ?'
        GrailsJobClassConstants.DEFAULT_GROUP == 'GRAILS_JOBS'
        GrailsJobClassConstants.DEFAULT_DESCRIPTION == 'Grails Job'
        GrailsJobClassConstants.DEFAULT_CONCURRENT
        GrailsJobClassConstants.DEFAULT_SESSION_REQUIRED
        GrailsJobClassConstants.DEFAULT_TRIGGERS_GROUP == 'GRAILS_TRIGGERS'
        GrailsJobClassConstants.DEFAULT_DURABILITY
        !GrailsJobClassConstants.DEFAULT_REQUESTS_RECOVERY
        GrailsJobClassConstants.DEFAULT_ENABLED
    }

    void 'the class is a private-constructor, final utility class'() {
        expect:
        java.lang.reflect.Modifier.isPrivate(GrailsJobClassConstants.getDeclaredConstructor().modifiers)
        java.lang.reflect.Modifier.isFinal(GrailsJobClassConstants.modifiers)

        when:
        GrailsJobClassConstants.getDeclaredConstructor().newInstance()

        then:
        thrown(IllegalAccessException)
    }

}
