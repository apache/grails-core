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
        assertScheduled this.getName()
        internalScheduler.triggerJob(new JobKey(this.getName(), internalJobArtefact.group), params ? new JobDataMap(params) : null)
    }

    @CompileDynamic
    static schedule(Long repeatInterval, Integer repeatCount = SimpleTrigger.REPEAT_INDEFINITELY, Map params = null) {
        assertScheduled this.getName()
        Assert.notNull repeatInterval, missingScheduleArgumentMessage(this.getName(), 'repeat interval')
        Assert.notNull repeatCount, missingScheduleArgumentMessage(this.getName(), 'repeat count')
        internalScheduleTrigger(TriggerUtils.buildSimpleTrigger(this.getName(), internalJobArtefact.group, repeatInterval, repeatCount), params)
    }

    @CompileDynamic
    static schedule(Date scheduleDate, Map params = null) {
        assertScheduled this.getName()
        Assert.notNull scheduleDate, missingScheduleArgumentMessage(this.getName(), 'schedule date')
        internalScheduleTrigger(TriggerUtils.buildDateTrigger(this.getName(), internalJobArtefact.group, scheduleDate), params)
    }

    @CompileDynamic
    static schedule(String cronExpression, Map params = null) {
        assertScheduled this.getName()
        Assert.notNull cronExpression, missingScheduleArgumentMessage(this.getName(), 'cron expression')
        internalScheduleTrigger(TriggerUtils.buildCronTrigger(this.getName(), internalJobArtefact.group, cronExpression), params)
    }

    static schedule(Trigger trigger, Map params = null) {
        assertScheduled this.getName()
        Assert.notNull trigger, missingScheduleArgumentMessage(this.getName(), 'trigger')

        JobKey jobKey = new JobKey(this.getName(), internalJobArtefact.group)
        Assert.isTrue trigger.jobKey == jobKey || (trigger instanceof MutableTrigger),
                'The trigger job key is not equal to the job key or the trigger is immutable'

        if (trigger instanceof MutableTrigger) {
            ((MutableTrigger) trigger).jobKey = jobKey
        }

        if (params) {
            trigger.jobDataMap.putAll(params)
        }
        internalScheduler.scheduleJob(trigger)
    }

    static removeJob() {
        assertScheduled this.getName()
        internalScheduler.deleteJob(new JobKey(this.getName(), internalJobArtefact.group))
    }

    static reschedule(Trigger trigger, Map params = null) {
        assertScheduled this.getName()
        Assert.notNull trigger, missingArgumentMessage(this.getName(), 'trigger')
        if (params) trigger.jobDataMap.putAll(params)
        internalScheduler.rescheduleJob(trigger.key, trigger)
    }

    static unschedule(String triggerName, String triggerGroup = GrailsJobClassConstants.DEFAULT_TRIGGERS_GROUP) {
        assertScheduled this.getName()
        Assert.notNull triggerName, missingArgumentMessage(this.getName(), 'trigger name')
        internalScheduler.unscheduleJob(TriggerKey.triggerKey(triggerName, triggerGroup))
    }

    private static internalScheduleTrigger(Trigger trigger, Map params = null) {
        if (params) {
            trigger.jobDataMap.putAll(params)
        }
        internalScheduler.scheduleJob(trigger)
    }

    /**
     * Verifies that the job class has been associated with a scheduler, which the plugin does for
     * every enabled job artefact while the application starts.
     *
     * @param jobClassName the name of the job class the method was called on
     */
    private static void assertScheduled(String jobClassName) {
        Assert.state internalScheduler != null && internalJobArtefact != null,
                "The job [${jobClassName}] is not registered with a Quartz scheduler. Only enabled job " +
                        'artefacts of a running application are registered, so check that the plugin is enabled ' +
                        '(quartz.pluginEnabled), that the job is enabled (its jobEnabled property) and that the ' +
                        'class is a job artefact of the application.' as String
    }

    /**
     * Builds the message reported when a method is called with a null argument.
     *
     * @param jobClassName the name of the job class the method was called on
     * @param argumentName the name of the argument that is null
     */
    private static String missingArgumentMessage(String jobClassName, String argumentName) {
        "The ${argumentName} passed for the job [${jobClassName}] is null." as String
    }

    /**
     * Builds the message reported when one of the schedule methods is called with a null argument. Such a
     * call resolves to {@link #schedule(Trigger, Map)} unless the caller is statically compiled, because the
     * runtime type of null carries no information, hence the hint about the arguments of the other methods.
     *
     * @param jobClassName the name of the job class the method was called on
     * @param argumentName the name of the argument that is null
     */
    private static String missingScheduleArgumentMessage(String jobClassName, String argumentName) {
        missingArgumentMessage(jobClassName, argumentName) + ' A null argument of any of the scheduling ' +
                'methods resolves to the one taking a trigger, so also check the repeat interval, repeat ' +
                'count, cron expression and date arguments of the method you called.'
    }

    static setScheduler(Scheduler scheduler) {
        internalScheduler = scheduler
    }

    static setGrailsJobClass(GrailsJobClass gjc) {
        internalJobArtefact = gjc
    }
}
