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

import grails.plugins.quartz.config.TriggersConfigBuilder
import org.quartz.CronTrigger
import spock.lang.Specification

/**
 * Trigger attributes declared in a job's triggers block are bound onto the trigger itself, which is
 * what lets a cron trigger carry a time zone. Covers the documented `timeZone` attribute.
 */
class CronTriggerTimeZoneSpec extends Specification {

    void 'a cron trigger is resolved against the configured time zone'() {
        given:
            CronTrigger trigger = buildCronTrigger(cronExpression: '0 15 6 * * ?', timeZone: timeZone)

        expect:
            trigger.timeZone == TimeZone.getTimeZone('Europe/Paris')

        where: 'the time zone is given either as a TimeZone or as its id'
            timeZone << [TimeZone.getTimeZone('Europe/Paris'), 'Europe/Paris']
    }

    void 'a cron trigger falls back to the local time zone when none is configured'() {
        given:
            CronTrigger trigger = buildCronTrigger(cronExpression: '0 15 6 * * ?')

        expect:
            trigger.timeZone == TimeZone.default
    }

    private static CronTrigger buildCronTrigger(Map attributes) {
        TriggersConfigBuilder builder = new TriggersConfigBuilder('TestJob', null)
        builder.build { cron(attributes) }
        Expando descriptor = builder.triggers['TestJob0']

        CustomTriggerFactoryBean factory = new CustomTriggerFactoryBean()
        factory.triggerClass = descriptor.triggerClass
        factory.triggerAttributes = descriptor.triggerAttributes
        factory.afterPropertiesSet()
        factory.object as CronTrigger
    }
}
