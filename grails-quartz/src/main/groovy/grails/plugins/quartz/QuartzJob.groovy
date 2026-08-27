/*
 * Copyright 2015 the original author or authors.
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

import grails.core.GrailsApplication
import grails.core.support.GrailsApplicationAware
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.quartz.JobDataMap
import org.quartz.JobKey
import org.quartz.Scheduler
import org.quartz.SimpleTrigger
import org.quartz.Trigger
import org.quartz.TriggerKey
import org.quartz.spi.MutableTrigger
import org.springframework.util.Assert

@CompileStatic
trait QuartzJob implements GrailsApplicationAware {

    private static Scheduler internalScheduler
    private static GrailsJobClass internalJobArtefact

    GrailsApplication grailsApplication

    static triggerNow(Map params = null) {
        internalScheduler.triggerJob(new JobKey(this.getName(), internalJobArtefact.group), params ? new JobDataMap(params) : null)
    }

    @CompileDynamic
    static schedule(Long repeatInterval, Integer repeatCount = SimpleTrigger.REPEAT_INDEFINITELY, Map params = null) {
        internalScheduleTrigger(TriggerUtils.buildSimpleTrigger(this.getName(), internalJobArtefact.group, repeatInterval, repeatCount), params)
    }

    @CompileDynamic
    static schedule(Date scheduleDate, Map params = null) {
        internalScheduleTrigger(TriggerUtils.buildDateTrigger(this.getName(), internalJobArtefact.group, scheduleDate), params)
    }

    @CompileDynamic
    static schedule(String cronExpression, Map params = null) {
        internalScheduleTrigger(TriggerUtils.buildCronTrigger(this.getName(), internalJobArtefact.group, cronExpression), params)
    }

    static schedule(Trigger trigger, Map params = null) {
        def jobKey = new JobKey(this.getName(), internalJobArtefact.group)
        Assert.isTrue trigger.jobKey == jobKey || (trigger instanceof MutableTrigger),
                'The trigger job key is not equal to the job key or the trigger is immutable'

        ((MutableTrigger)trigger).jobKey = jobKey

        if (params) {
            trigger.jobDataMap.putAll(params)
        }
        internalScheduler.scheduleJob(trigger)
    }

    static removeJob() {
        internalScheduler.deleteJob(new JobKey(this.getName(), internalJobArtefact.group))
    }

    static reschedule(Trigger trigger, Map params = null) {
        if (params) trigger.jobDataMap.putAll(params)
        internalScheduler.rescheduleJob(trigger.key, trigger)
    }

    static unschedule(String triggerName, String triggerGroup = GrailsJobClassConstants.DEFAULT_TRIGGERS_GROUP) {
        internalScheduler.unscheduleJob(TriggerKey.triggerKey(triggerName, triggerGroup))
    }

    private static internalScheduleTrigger(Trigger trigger, Map params = null) {
        if (params) {
            trigger.jobDataMap.putAll(params)
        }
        internalScheduler.scheduleJob(trigger)
    }

    static setScheduler(Scheduler scheduler) {
        internalScheduler = scheduler
    }

    static setGrailsJobClass(GrailsJobClass gjc) {
        internalJobArtefact = gjc
    }
}
