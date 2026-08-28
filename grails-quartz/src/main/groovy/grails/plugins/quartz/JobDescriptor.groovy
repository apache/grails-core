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

import groovy.transform.CompileStatic
import org.quartz.JobDetail
import org.quartz.Scheduler
import org.quartz.Trigger

/**
 * JobDescriptor that stores information about the Quartz job to show on webapp.
 *
 * @author Marco Mornati (mmornati@byte-code.com)
 * @author Sergey Nebolsin (nebolsin@gmail.com)
 *
 * @since 0.4
 */
@CompileStatic
class JobDescriptor {

    JobDetail jobDetail

    List<TriggerDescriptor> triggerDescriptors

    static build(JobDetail jobDetail, Scheduler scheduler) {
        def job = new JobDescriptor(jobDetail: jobDetail)
        job.triggerDescriptors = (List <TriggerDescriptor>)scheduler.getTriggersOfJob(jobDetail.key).collect { trigger ->
            TriggerDescriptor.build(job, (Trigger)trigger, scheduler)
        }
        return job
    }

    String getName() {
        jobDetail.key.name
    }

    String getGroup() {
        jobDetail.key.group
    }
}
