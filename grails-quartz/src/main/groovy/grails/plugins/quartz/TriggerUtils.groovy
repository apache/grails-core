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

import groovy.transform.CompileStatic
import org.quartz.CronScheduleBuilder
import org.quartz.CronTrigger
import org.quartz.SimpleScheduleBuilder
import org.quartz.SimpleTrigger
import org.quartz.Trigger
import org.quartz.TriggerBuilder

/**
 * The util class which helps to build triggers for schedule methods.
 *
 * @author Vitalii Samolovskikh aka Kefir
 */
@CompileStatic
class TriggerUtils {

    private static String generateTriggerName() {
        'GRAILS_' + UUID.randomUUID().toString()
    }

    static Trigger buildDateTrigger(String jobName, String jobGroup, Date scheduleDate) {
        return TriggerBuilder.newTrigger()
                .withIdentity(generateTriggerName(), GrailsJobClassConstants.DEFAULT_TRIGGERS_GROUP)
                .withPriority(6)
                .forJob(jobName, jobGroup)
                .startAt(scheduleDate)
                .build()
    }

    static SimpleTrigger buildSimpleTrigger(String jobName, String jobGroup, long repeatInterval, int repeatCount) {
        return TriggerBuilder.newTrigger()
                .withIdentity(generateTriggerName(), GrailsJobClassConstants.DEFAULT_TRIGGERS_GROUP)
                .withPriority(6)
                .forJob(jobName, jobGroup)
                .withSchedule(SimpleScheduleBuilder.simpleSchedule().withIntervalInMilliseconds(repeatInterval).withRepeatCount(repeatCount))
                .build()
    }

    static CronTrigger buildCronTrigger(String jobName, String jobGroup, String cronExpression) {
        return TriggerBuilder.newTrigger()
                .withIdentity(generateTriggerName(), GrailsJobClassConstants.DEFAULT_TRIGGERS_GROUP)
                .withPriority(6)
                .forJob(jobName, jobGroup)
                .withSchedule(CronScheduleBuilder.cronSchedule(cronExpression))
                .build()
    }
}
