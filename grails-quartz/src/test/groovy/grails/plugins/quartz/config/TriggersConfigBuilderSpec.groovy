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

package grails.plugins.quartz.config

import grails.plugins.quartz.CustomTriggerFactoryBean
import grails.plugins.quartz.GrailsJobClassConstants as Constants
import spock.lang.Specification

/**
 * Create 10 triggers with different attributes.
 *
 * @author Sergey Nebolsin (nebolsin@gmail.com)
 */
class TriggersConfigBuilderSpec extends Specification {


    void 'testConfigBuilder'() {
        setup:
            def builder = new TriggersConfigBuilder('TestJob', null)
            def closure = {
                simple()
                simple timeout: 1000
                simple startDelay: 500
                simple startDelay: 500, timeout: 1000
                simple startDelay: 500, timeout: 1000, repeatCount: 3
                simple name: 'everySecond', timeout: 1000
                cron()
                cron cronExpression: '0 15 6 * * ?'
                cron name: 'myTrigger', cronExpression: '0 15 6 * * ?'
                simple startDelay: 500, timeout: 1000, repeatCount: 0
            }
            builder.build(closure)
        expect:
            assert 10 == builder.triggers.size(): 'Invalid triggers count'
        when: 'TestJob0'
            def triggerName = 'TestJob0'
        then: 'Verify attributes for TestJob0'
            assert builder.triggers[triggerName]?.clazz == CustomTriggerFactoryBean
            assertPropertiesEquals(new Expando(
                    name: triggerName,
                    group: Constants.DEFAULT_TRIGGERS_GROUP,
                    startDelay: Constants.DEFAULT_START_DELAY,
                    repeatInterval: Constants.DEFAULT_REPEAT_INTERVAL,
                    repeatCount: Constants.DEFAULT_REPEAT_COUNT,
            ), builder.triggers[triggerName].triggerAttributes)
        when: 'TestJob1'
            triggerName = 'TestJob1'
        then: 'Verify attributes for TestJob1'
            assert builder.triggers[triggerName]?.clazz == CustomTriggerFactoryBean
            assertPropertiesEquals(new Expando(
                    name: triggerName,
                    group: Constants.DEFAULT_TRIGGERS_GROUP,
                    startDelay: Constants.DEFAULT_START_DELAY,
                    repeatInterval: 1000,
                    repeatCount: Constants.DEFAULT_REPEAT_COUNT,
            ), builder.triggers[triggerName].triggerAttributes)
        when: 'TestJob2'
            triggerName = 'TestJob2'
        then: 'Verify attributes for TestJob2'
            assert builder.triggers[triggerName]?.clazz == CustomTriggerFactoryBean
            assertPropertiesEquals(new Expando(
                    name: triggerName,
                    group: Constants.DEFAULT_TRIGGERS_GROUP,
                    startDelay: 500,
                    repeatInterval: Constants.DEFAULT_REPEAT_INTERVAL,
                    repeatCount: Constants.DEFAULT_REPEAT_COUNT,
            ), builder.triggers[triggerName].triggerAttributes)
        when: 'TestJob3'
            triggerName = 'TestJob3'
        then: 'Verify attributes for TestJob3'
            assert builder.triggers[triggerName]?.clazz == CustomTriggerFactoryBean
            assertPropertiesEquals(new Expando(
                    name: triggerName,
                    group: Constants.DEFAULT_TRIGGERS_GROUP,
                    startDelay: 500,
                    repeatInterval: 1000,
                    repeatCount: Constants.DEFAULT_REPEAT_COUNT,
            ), builder.triggers[triggerName].triggerAttributes)
        when: 'TestJob4'
            triggerName = 'TestJob4'
        then: 'Verify attributes for TestJob4'
            assert builder.triggers[triggerName]?.clazz == CustomTriggerFactoryBean
            assertPropertiesEquals(new Expando(
                    name: triggerName,
                    group: Constants.DEFAULT_TRIGGERS_GROUP,
                    startDelay: 500,
                    repeatInterval: 1000,
                    repeatCount: 3,
            ), builder.triggers[triggerName].triggerAttributes)
        when: 'everySecond'
            triggerName = 'everySecond'
        then: 'Verify attribute everySecond'
            assert builder.triggers[triggerName]?.clazz == CustomTriggerFactoryBean
            assertPropertiesEquals(new Expando(
                    name: triggerName,
                    group: Constants.DEFAULT_TRIGGERS_GROUP,
                    startDelay: Constants.DEFAULT_START_DELAY,
                    repeatInterval: 1000,
                    repeatCount: Constants.DEFAULT_REPEAT_COUNT,
            ), builder.triggers[triggerName].triggerAttributes
            )
        when: 'TestJob5'
            triggerName = 'TestJob5'
        then: 'Verify attributes TestJob5'
            assert builder.triggers[triggerName]?.clazz == CustomTriggerFactoryBean
            assertPropertiesEquals(new Expando(
                    name: triggerName,
                    group: Constants.DEFAULT_TRIGGERS_GROUP,
                    startDelay: Constants.DEFAULT_START_DELAY,
                    cronExpression: Constants.DEFAULT_CRON_EXPRESSION,
            ), builder.triggers[triggerName].triggerAttributes
            )
        when: 'TestJob6'
            triggerName = 'TestJob6'
        then: 'Verify attributes TestJob6'
            assert builder.triggers[triggerName]?.clazz == CustomTriggerFactoryBean
            assertPropertiesEquals(new Expando(
                    name: triggerName,
                    group: Constants.DEFAULT_TRIGGERS_GROUP,
                    cronExpression: '0 15 6 * * ?',
                    startDelay: Constants.DEFAULT_START_DELAY,
            ), builder.triggers[triggerName].triggerAttributes)
        when: 'myTrigger'
            triggerName = 'myTrigger'
        then: 'Verify attribute myTrigger'
            assert builder.triggers[triggerName]?.clazz == CustomTriggerFactoryBean
            assertPropertiesEquals(new Expando(
                    name: triggerName,
                    group: Constants.DEFAULT_TRIGGERS_GROUP,
                    startDelay: Constants.DEFAULT_START_DELAY,
                    cronExpression: '0 15 6 * * ?',
            ), builder.triggers[triggerName].triggerAttributes)
        when: 'TestJob7'
            triggerName = 'TestJob7'
        then: 'Verify attribute TestJob7'
            assert builder.triggers[triggerName]?.clazz == CustomTriggerFactoryBean
            assertPropertiesEquals(new Expando(
                    name: triggerName,
                    group: Constants.DEFAULT_TRIGGERS_GROUP,
                    startDelay: 500,
                    repeatInterval: 1000,
                    repeatCount: 0,
            ), builder.triggers[triggerName].triggerAttributes)
    }

    private static void assertPropertiesEquals(expected, actual) {
        expected.properties.each { entry ->
            assert actual[entry.key] == entry.value, "Unexpected value for property: ${entry.key}"
        }
        assert actual.size() == expected.properties?.size(), 'Different number of properties'
    }
}
