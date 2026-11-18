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
import org.quartz.DailyTimeIntervalTrigger
import org.quartz.DateBuilder
import org.quartz.SimpleTrigger
import org.quartz.TimeOfDay
import org.quartz.Trigger
import org.quartz.impl.triggers.DailyTimeIntervalTriggerImpl
import spock.lang.Specification

/**
 * Tests for CustomTriggerFactoryBean
 *
 * @author Vitalii Samolovskikh aka Kefir
 */
class CustomTriggerFactoryBeanSpec extends Specification {

    private static final String CRON_EXPRESSION = '0 15 6 * * ?'
    private static final TimeOfDay START_TIME = new TimeOfDay(10, 0)
    private static final TimeOfDay END_TIME = new TimeOfDay(11, 30)

    void 'testFactory'() {
        setup:
            def builder = new TriggersConfigBuilder('TestJob', null)
            def closure = {
                simple name: 'simple', group: 'group', startDelay: 500, repeatInterval: 1000, repeatCount: 3
                cron name: 'cron', group: 'group', cronExpression: CRON_EXPRESSION
                custom name: 'custom', group: 'group', triggerClass: DailyTimeIntervalTriggerImpl,
                        startTimeOfDay: START_TIME, endTimeOfDay: END_TIME,
                        repeatIntervalUnit: DateBuilder.IntervalUnit.MINUTE, repeatInterval: 5
            }
            builder.build(closure)

            Map<String, Trigger> triggers = [:]

            builder.triggers.values().each {
                CustomTriggerFactoryBean factory = new CustomTriggerFactoryBean()
                factory.setTriggerClass(it.triggerClass)
                factory.setTriggerAttributes(it.triggerAttributes)
                factory.afterPropertiesSet()
                Trigger trigger = factory.getObject() as Trigger
                triggers.put(trigger.key.name, trigger)
            }

        expect:
            assert triggers['simple'] instanceof SimpleTrigger
            SimpleTrigger simpleTrigger = triggers['simple'] as SimpleTrigger
            assert 'simple' == simpleTrigger.key.name
            assert 'group' == simpleTrigger.key.group
            assert 1000 == simpleTrigger.repeatInterval
            assert 3 == simpleTrigger.repeatCount

            assert triggers['cron'] instanceof CronTrigger
            CronTrigger cronTrigger = triggers['cron'] as CronTrigger
            assert 'cron' == cronTrigger.key.name
            assert 'group' == cronTrigger.key.group
            assert CRON_EXPRESSION == cronTrigger.getCronExpression()

            assert triggers['custom'] instanceof DailyTimeIntervalTrigger
            DailyTimeIntervalTrigger customTrigger = triggers['custom'] as DailyTimeIntervalTrigger
            assert 'custom' == customTrigger.key.name
            assert 'group' == customTrigger.key.group
            assert START_TIME == customTrigger.startTimeOfDay
            assert END_TIME == customTrigger.endTimeOfDay
            assert DateBuilder.IntervalUnit.MINUTE == customTrigger.repeatIntervalUnit
            assert 5 == customTrigger.repeatInterval
    }

    void 'the misfire instruction a trigger declares is carried by the trigger'() {
        setup:
            def builder = new TriggersConfigBuilder('TestJob', null)
            builder.build {
                simple name: 'simple', repeatInterval: 1000,
                        misfireInstruction: SimpleTrigger.MISFIRE_INSTRUCTION_RESCHEDULE_NEXT_WITH_REMAINING_COUNT
                cron name: 'cron', cronExpression: CRON_EXPRESSION,
                        misfireInstruction: CronTrigger.MISFIRE_INSTRUCTION_DO_NOTHING
            }

            Map<String, Trigger> triggers = [:]

            builder.triggers.values().each {
                CustomTriggerFactoryBean factory = new CustomTriggerFactoryBean()
                factory.setTriggerClass(it.triggerClass)
                factory.setTriggerAttributes(it.triggerAttributes)
                factory.afterPropertiesSet()
                Trigger trigger = factory.getObject() as Trigger
                triggers.put(trigger.key.name, trigger)
            }

        expect:
            triggers['simple'].misfireInstruction == SimpleTrigger.MISFIRE_INSTRUCTION_RESCHEDULE_NEXT_WITH_REMAINING_COUNT
            triggers['cron'].misfireInstruction == CronTrigger.MISFIRE_INSTRUCTION_DO_NOTHING
    }

    void 'a trigger uses the smart misfire policy of its type unless it declares an instruction'() {
        setup:
            def builder = new TriggersConfigBuilder('TestJob', null)
            builder.build {
                simple name: 'simple', repeatInterval: 1000
                cron name: 'cron', cronExpression: CRON_EXPRESSION
            }

            Map<String, Trigger> triggers = [:]

            builder.triggers.values().each {
                CustomTriggerFactoryBean factory = new CustomTriggerFactoryBean()
                factory.setTriggerClass(it.triggerClass)
                factory.setTriggerAttributes(it.triggerAttributes)
                factory.afterPropertiesSet()
                Trigger trigger = factory.getObject() as Trigger
                triggers.put(trigger.key.name, trigger)
            }

        expect:
            triggers['simple'].misfireInstruction == Trigger.MISFIRE_INSTRUCTION_SMART_POLICY
            triggers['cron'].misfireInstruction == Trigger.MISFIRE_INSTRUCTION_SMART_POLICY
    }
}
