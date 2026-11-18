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

import grails.artefact.Artefact
import groovy.transform.CompileStatic
import org.quartz.CronTrigger
import org.quartz.JobDataMap
import org.quartz.JobKey
import org.quartz.Scheduler
import org.quartz.SimpleTrigger
import org.quartz.Trigger
import org.quartz.TriggerKey
import org.quartz.impl.triggers.SimpleTriggerImpl
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Verifies the scheduling methods the {@link QuartzJob} trait adds to every job artefact.
 */
class QuartzJobSpec extends Specification {

    Scheduler scheduler = Mock(Scheduler)

    void setup() {
        SchedulingJob.scheduler = scheduler
        SchedulingJob.grailsJobClass = new DefaultGrailsJobClass(SchedulingJob)
    }

    void cleanup() {
        SchedulingJob.scheduler = null
        SchedulingJob.grailsJobClass = null
    }

    void 'triggerNow fires the job the trait was applied to'() {
        when:
            SchedulingJob.triggerNow()

        then:
            1 * scheduler.triggerJob(new JobKey(SchedulingJob.name, 'GRAILS_JOBS'), null)
    }

    void 'triggerNow passes the given parameters as job data'() {
        when:
            SchedulingJob.triggerNow(foo: 'bar')

        then:
            1 * scheduler.triggerJob(new JobKey(SchedulingJob.name, 'GRAILS_JOBS'), { JobDataMap jobData ->
                jobData.foo == 'bar'
            })
    }

    void 'a repeat interval schedules a simple trigger for the job'() {
        when:
            SchedulingJob.schedule(1_000L, 4, [foo: 'bar'])

        then:
            1 * scheduler.scheduleJob({ SimpleTrigger trigger ->
                trigger.jobKey == new JobKey(SchedulingJob.name, 'GRAILS_JOBS')
                        && trigger.key.group == 'GRAILS_TRIGGERS'
                        && trigger.repeatInterval == 1_000L
                        && trigger.repeatCount == 4
                        && trigger.jobDataMap.foo == 'bar'
            })
    }

    void 'a repeat interval repeats indefinitely unless a repeat count is given'() {
        when:
            SchedulingJob.schedule(1_000L)

        then:
            1 * scheduler.scheduleJob({ SimpleTrigger trigger ->
                trigger.repeatCount == SimpleTrigger.REPEAT_INDEFINITELY
            })
    }

    void 'a date schedules a single execution of the job'() {
        given:
            Date scheduleDate = new Date(1_000_000L)

        when:
            SchedulingJob.schedule(scheduleDate, [foo: 'bar'])

        then:
            1 * scheduler.scheduleJob({ Trigger trigger ->
                trigger.jobKey == new JobKey(SchedulingJob.name, 'GRAILS_JOBS')
                        && trigger.startTime == scheduleDate
                        && trigger.jobDataMap.foo == 'bar'
            })
    }

    void 'a cron expression schedules a cron trigger for the job'() {
        when:
            SchedulingJob.schedule('0 0 6 * * ?', [foo: 'bar'])

        then:
            1 * scheduler.scheduleJob({ CronTrigger trigger ->
                trigger.jobKey == new JobKey(SchedulingJob.name, 'GRAILS_JOBS')
                        && trigger.cronExpression == '0 0 6 * * ?'
                        && trigger.jobDataMap.foo == 'bar'
            })
    }

    void 'a trigger built by the application is re-keyed to the job before it is scheduled'() {
        given:
            SimpleTriggerImpl trigger = new SimpleTriggerImpl('myTrigger', 'myGroup')
            trigger.jobKey = new JobKey('someOtherJob', 'someOtherGroup')

        when:
            SchedulingJob.schedule(trigger, [foo: 'bar'])

        then:
            1 * scheduler.scheduleJob(trigger)
            trigger.jobKey == new JobKey(SchedulingJob.name, 'GRAILS_JOBS')
            trigger.jobDataMap.foo == 'bar'
    }

    void 'an immutable trigger already keyed to the job is scheduled unchanged'() {
        given:
            JobKey jobKey = new JobKey(SchedulingJob.name, 'GRAILS_JOBS')
            Trigger trigger = Mock(Trigger) {
                getJobKey() >> jobKey
            }

        when:
            SchedulingJob.schedule(trigger)

        then:
            1 * scheduler.scheduleJob(trigger)
    }

    void 'an immutable trigger keyed to another job is rejected'() {
        given:
            Trigger trigger = Mock(Trigger) {
                getJobKey() >> new JobKey('someOtherJob', 'someOtherGroup')
            }

        when:
            SchedulingJob.schedule(trigger)

        then:
            IllegalArgumentException e = thrown()
            e.message == 'The trigger job key is not equal to the job key or the trigger is immutable'
            0 * scheduler.scheduleJob(_)
    }

    void 'rescheduling replaces the trigger registered under its own key'() {
        given:
            SimpleTriggerImpl trigger = new SimpleTriggerImpl('myTrigger', 'myGroup')

        when:
            SchedulingJob.reschedule(trigger, [foo: 'bar'])

        then:
            1 * scheduler.rescheduleJob(new TriggerKey('myTrigger', 'myGroup'), trigger)
            trigger.jobDataMap.foo == 'bar'
    }

    void 'unscheduling uses the default trigger group unless one is given'() {
        when:
            SchedulingJob.unschedule('myTrigger')

        then:
            1 * scheduler.unscheduleJob(new TriggerKey('myTrigger', 'GRAILS_TRIGGERS'))

        when:
            SchedulingJob.unschedule('myTrigger', 'myGroup')

        then:
            1 * scheduler.unscheduleJob(new TriggerKey('myTrigger', 'myGroup'))
    }

    void 'removing the job deletes it from the scheduler'() {
        when:
            SchedulingJob.removeJob()

        then:
            1 * scheduler.deleteJob(new JobKey(SchedulingJob.name, 'GRAILS_JOBS'))
    }

    void 'scheduling a job with a null argument reports the trigger as missing and hints at the other arguments'() {
        when: 'a null is passed to schedule, which always resolves to the method taking a trigger'
            SchedulingJob.schedule(null)

        then:
            IllegalArgumentException e = thrown()
            e.message == "The trigger passed for the job [${SchedulingJob.name}] is null. A null argument of " +
                    'any of the scheduling methods resolves to the one taking a trigger, so also check the ' +
                    'repeat interval, repeat count, cron expression and date arguments of the method you called.'
            0 * scheduler.scheduleJob(_)
    }

    @Unroll
    void 'scheduling a job with a null #argument reports which argument is missing'() {
        when: 'a statically compiled caller resolves the method by the declared type of its arguments'
            invocation.call()

        then:
            IllegalArgumentException e = thrown()
            e.message.startsWith("The ${argument} passed for the job [${SchedulingJob.name}] is null.")
            0 * scheduler.scheduleJob(_)

        where:
            argument          | invocation
            'repeat interval' | { StaticallyCompiledScheduler.scheduleWithInterval(null) }
            'repeat count'    | { StaticallyCompiledScheduler.scheduleWithRepeatCount(1_000L, null) }
            'schedule date'   | { StaticallyCompiledScheduler.scheduleAtDate(null) }
            'cron expression' | { StaticallyCompiledScheduler.scheduleWithCron(null) }
            'trigger'         | { StaticallyCompiledScheduler.scheduleWithTrigger(null) }
    }

    void 'rescheduling a job with a null trigger reports the missing argument'() {
        when:
            SchedulingJob.reschedule(null)

        then:
            IllegalArgumentException e = thrown()
            e.message.startsWith("The trigger passed for the job [${SchedulingJob.name}] is null.")
            0 * scheduler.rescheduleJob(_, _)
    }

    void 'unscheduling a null trigger name reports the missing argument'() {
        when:
            SchedulingJob.unschedule(null)

        then:
            IllegalArgumentException e = thrown()
            e.message.startsWith("The trigger name passed for the job [${SchedulingJob.name}] is null.")
            0 * scheduler.unscheduleJob(_)
    }

    @Unroll
    void 'a job that is not registered with a scheduler reports it instead of failing with a null pointer'() {
        when:
            invocation.call()

        then:
            IllegalStateException e = thrown()
            e.message.startsWith("The job [${UnregisteredJob.name}] is not registered with a Quartz scheduler.")

        where:
            invocation << [
                    { UnregisteredJob.triggerNow() },
                    { UnregisteredJob.schedule(1_000L) },
                    { UnregisteredJob.schedule(new Date()) },
                    { UnregisteredJob.schedule('0 0 6 * * ?') },
                    { UnregisteredJob.schedule(new SimpleTriggerImpl('myTrigger', 'myGroup')) },
                    { UnregisteredJob.reschedule(new SimpleTriggerImpl('myTrigger', 'myGroup')) },
                    { UnregisteredJob.unschedule('myTrigger') },
                    { UnregisteredJob.removeJob() },
            ]
    }

    void 'a job whose scheduler is set but which has no artefact is reported as not registered'() {
        given:
            PartiallyRegisteredJob.scheduler = scheduler

        when:
            PartiallyRegisteredJob.triggerNow()

        then:
            IllegalStateException e = thrown()
            e.message.startsWith("The job [${PartiallyRegisteredJob.name}] is not registered with a Quartz scheduler.")
            0 * scheduler.triggerJob(_, _)

        cleanup:
            PartiallyRegisteredJob.scheduler = null
    }
}

@CompileStatic
class StaticallyCompiledScheduler {

    static void scheduleWithInterval(Long repeatInterval) {
        SchedulingJob.schedule(repeatInterval)
    }

    static void scheduleWithRepeatCount(Long repeatInterval, Integer repeatCount) {
        SchedulingJob.schedule(repeatInterval, repeatCount)
    }

    static void scheduleAtDate(Date scheduleDate) {
        SchedulingJob.schedule(scheduleDate)
    }

    static void scheduleWithCron(String cronExpression) {
        SchedulingJob.schedule(cronExpression)
    }

    static void scheduleWithTrigger(Trigger trigger) {
        SchedulingJob.schedule(trigger)
    }
}

@Artefact('Job')
class SchedulingJob {
    void execute() {}
}

@Artefact('Job')
class UnregisteredJob {
    void execute() {}
}

@Artefact('Job')
class PartiallyRegisteredJob {
    void execute() {}
}
