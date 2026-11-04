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

import grails.plugins.quartz.JobManagerService
import grails.testing.mixin.integration.Integration
import org.quartz.JobExecutionContext
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

/**
 * Verifies what a running application does with a job whose execution outlives the fire that started it:
 * executions are serialized when the job asks for it, and a running execution can be observed and stopped.
 */
@Integration(applicationClass = Application)
class QuartzLongRunningJobSpec extends Specification {

    private static final String JOBS_GROUP = 'GRAILS_JOBS'

    @Autowired
    JobManagerService jobManagerService

    void cleanup() {
        LongRunningJob.RELEASE.countDown()
    }

    void 'a job which is not concurrent is not executed while an earlier execution is still running'() {
        given:
            PollingConditions conditions = new PollingConditions(timeout: 30)

        when: 'a job that runs until it is released is triggered twice'
            LongRunningJob.triggerNow()

        then:
            conditions.eventually {
                assert LongRunningJob.STARTED.get() == 1
            }

        and: 'the execution is reported as running, with the moment it started'
            jobManagerService.runningJobs.any { JobExecutionContext context ->
                context.jobDetail.key.name == LongRunningJob.name && context.fireTime != null
            }

        when: 'the application tries to stop an execution of a job which cannot be interrupted'
            jobManagerService.interruptJob(JOBS_GROUP, LongRunningJob.name)

        then: 'it is told that the job does not support it'
            Exception e = thrown()
            e.message.contains('does not declare an interrupt() method')

        when: 'a second fire arrives while the first execution is still in flight'
            LongRunningJob.triggerNow()

        then: 'it waits rather than running a second execution alongside the first'
            conditions.within(2) {
                assert LongRunningJob.STARTED.get() == 1
            }

        when: 'the first execution finishes'
            LongRunningJob.RELEASE.countDown()

        then: 'the fire that was held runs'
            conditions.eventually {
                assert LongRunningJob.STARTED.get() == 2
                assert LongRunningJob.FINISHED.get() == 2
            }
    }

    void 'a job that runs for too long is stopped by interrupting it'() {
        given:
            PollingConditions conditions = new PollingConditions(timeout: 30)

        when:
            InterruptibleJob.triggerNow()

        then:
            conditions.eventually {
                assert InterruptibleJob.STARTED.get() == 1
            }

        when: 'the application interrupts the job it finds running'
            jobManagerService.interruptJob(JOBS_GROUP, InterruptibleJob.name)

        then: 'the execution stops, and the scheduler is left with nothing running'
            conditions.eventually {
                assert InterruptibleJob.INTERRUPTED.get() == 1
                assert jobManagerService.runningJobs.every { JobExecutionContext context ->
                    context.jobDetail.key.name != InterruptibleJob.name
                }
            }
    }
}
