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

import org.quartz.*
import org.quartz.impl.StdSchedulerFactory
import spock.lang.Specification

/**
 * Unit tests for JobDescriptor.
 *
 * @author Vitalii Samolovskikh aka Kefir
 */
class JobDescriptorSpec extends Specification {

    private Scheduler scheduler
    private JobDetail job
    private Trigger trigger


    def setup() {
        scheduler = StdSchedulerFactory.getDefaultScheduler()
        scheduler.start()
        job = JobBuilder.newJob(TestQuartzJob).withIdentity(new JobKey("job", "group")).build()
        trigger = TriggerBuilder.newTrigger()
                .withIdentity(new TriggerKey("trigger", "group"))
                .withSchedule(SimpleScheduleBuilder.simpleSchedule().withIntervalInMinutes(2).repeatForever())
                .startNow()
                .build()
        scheduler.scheduleJob(job, trigger)
    }

    def cleanup() {
        scheduler.shutdown()
    }

    void 'JobDescriptor builds correctly from a job and scheduler'() {
        when:
            JobDescriptor descriptor = JobDescriptor.build(job, scheduler)
        then:
            descriptor.name == 'job'
            descriptor.group == 'group'
            descriptor.triggerDescriptors.size() == 1
            descriptor.triggerDescriptors[0].name == 'trigger'
    }

}
