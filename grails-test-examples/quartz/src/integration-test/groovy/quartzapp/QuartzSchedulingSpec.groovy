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

package quartzapp

import grails.testing.mixin.integration.Integration
import org.quartz.JobKey
import org.quartz.Scheduler
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

/**
 * Verifies that a running application really schedules and executes the jobs declared under
 * grails-app/jobs, using the defaults the plugin ships with.
 */
@Integration(applicationClass = Application)
class QuartzSchedulingSpec extends Specification {

    @Autowired
    Scheduler quartzScheduler

    void 'the scheduler is started once the application has bootstrapped'() {
        expect:
            quartzScheduler.isStarted()
            !quartzScheduler.isInStandbyMode()
            !quartzScheduler.isShutdown()
    }

    void 'every job artefact is registered with the scheduler'() {
        expect:
            quartzScheduler.checkExists(JobKey.jobKey(RepeatingJob.name, 'GRAILS_JOBS'))
            quartzScheduler.checkExists(JobKey.jobKey(OnDemandJob.name, 'GRAILS_JOBS'))
    }

    void 'a job with a simple trigger is executed without any intervention'() {
        given:
            PollingConditions conditions = new PollingConditions(timeout: 30)

        expect:
            conditions.eventually {
                assert RepeatingJob.EXECUTIONS.get() > 0
            }
    }

    void 'a global job listener registered by the application observes every job execution'() {
        given:
            PollingConditions conditions = new PollingConditions(timeout: 30)

        expect: 'the listener registered in BootStrap wraps the executions the scheduler drives'
            conditions.eventually {
                assert RecordingJobListener.BEFORE.get() > 0
                assert RecordingJobListener.AFTER.get() > 0
            }
    }

    void 'a job without triggers only runs when it is triggered through the QuartzJob trait'() {
        given:
            PollingConditions conditions = new PollingConditions(timeout: 30)
            int before = OnDemandJob.EXECUTIONS.get()

        when:
            OnDemandJob.triggerNow()

        then:
            conditions.eventually {
                assert OnDemandJob.EXECUTIONS.get() > before
            }
    }

    void 'a job scheduled at runtime with a repeat interval is executed by the scheduler'() {
        given:
            PollingConditions conditions = new PollingConditions(timeout: 30)
            int before = OnDemandJob.EXECUTIONS.get()

        when:
            OnDemandJob.schedule(200L, 2, [foo: 'bar'])

        then:
            conditions.eventually {
                assert OnDemandJob.EXECUTIONS.get() > before
            }
    }

    void 'a job scheduled at runtime with a cron expression is registered with the scheduler'() {
        given:
            JobKey jobKey = JobKey.jobKey(OnDemandJob.name, 'GRAILS_JOBS')
            int before = quartzScheduler.getTriggersOfJob(jobKey).size()

        when:
            OnDemandJob.schedule('0 0 6 * * ?')

        then:
            quartzScheduler.getTriggersOfJob(jobKey).size() == before + 1
    }

    void 'scheduling a job with a null argument reports the null instead of failing with a null pointer'() {
        when: 'the value an application passes to schedule turns out to be null'
            OnDemandJob.schedule(null)

        then:
            IllegalArgumentException e = thrown()
            e.message.startsWith("The trigger passed for the job [${OnDemandJob.name}] is null.")
            e.message.contains('resolves to the one taking a trigger')
    }

    void 'a job whose trigger can never fire does not keep the application from starting'() {
        given:
            JobKey jobKey = JobKey.jobKey(NeverFiringJob.name, 'GRAILS_JOBS')

        expect: 'the application is up, with the job registered but without the trigger it declared'
            quartzScheduler.isStarted()
            quartzScheduler.checkExists(jobKey)
            quartzScheduler.getTriggersOfJob(jobKey).isEmpty()
    }

    void 'scheduling a job that the scheduler does not know about reports why'() {
        when: 'a job that is turned off is scheduled at runtime'
            DisabledJob.triggerNow()

        then:
            IllegalStateException e = thrown()
            e.message.startsWith("The job [${DisabledJob.name}] is not registered with a Quartz scheduler.")

        and: 'it never reached the scheduler in the first place'
            !quartzScheduler.checkExists(JobKey.jobKey(DisabledJob.name, 'GRAILS_JOBS'))
    }
}
