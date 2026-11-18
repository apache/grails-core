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

import org.quartz.JobKey
import org.quartz.Scheduler
import org.quartz.TriggerKey
import org.quartz.impl.matchers.GroupMatcher

/**
 * JobManagerService simplifies interaction with the Quartz Scheduler from Grails application.
 *
 * @author Marco Mornati (mmornati@byte-code.com)
 * @author Sergey Nebolsin (nebolsin@gmail.com)
 *
 * @since 0.4
 */
class JobManagerService {

    Scheduler quartzScheduler

    /**
     * Returns all the jobs, registered in the Quartz Scheduler, grouped bu their corresponding job groups.
     *
     * @return Map <String , List<JobDescriptor>> with job group names as keys
     */
    Map<String, List<JobDescriptor>> getAllJobs() {
        quartzScheduler.jobGroupNames.collectEntries([:]) { group -> [(group): getJobs(group)] }
    }

    /**
     * Returns all the jobs, registered in the Quartz Scheduler, which belong to the specified group.
     *
     * @param group — the jobs group name
     * @return a list of corresponding JobDescriptor objects
     */
    List<JobDescriptor> getJobs(String group) {
        List<JobDescriptor> list = new ArrayList<JobDescriptor>()
        quartzScheduler.getJobKeys(GroupMatcher.groupEquals(group)).each { jobKey ->
            def jobDetail = quartzScheduler.getJobDetail(jobKey)
            if (jobDetail != null) {
                list.add(JobDescriptor.build(jobDetail, quartzScheduler))
            }
        }
        return list
    }

    /**
     * Returns a list of all currently executing jobs.
     *
     * @return a List<JobExecutionContext>, containing all currently executing jobs.
     */
    def getRunningJobs() {
        quartzScheduler.getCurrentlyExecutingJobs()
    }

    def pauseJob(String group, String name) {
        quartzScheduler.pauseJob(new JobKey(name, group))
    }

    def resumeJob(String group, String name) {
        quartzScheduler.resumeJob(new JobKey(name, group))
    }

    def pauseTrigger(String group, String name) {
        quartzScheduler.pauseTrigger(new TriggerKey(name, group))
    }

    def resumeTrigger(String group, String name) {
        quartzScheduler.resumeTrigger(new TriggerKey(name, group))
    }

    def pauseTriggerGroup(String group) {
        quartzScheduler.pauseTriggers(GroupMatcher.groupEquals(group))
    }

    def resumeTriggerGroup(String group) {
        quartzScheduler.resumeTriggers(GroupMatcher.groupEquals(group))
    }

    def pauseJobGroup(String group) {
        quartzScheduler.pauseJobs(GroupMatcher.groupEquals(group))
    }

    def resumeJobGroup(String group) {
        quartzScheduler.resumeJobs(GroupMatcher.groupEquals(group))
    }

    def pauseAll() {
        quartzScheduler.pauseAll()
    }

    def resumeAll() {
        quartzScheduler.resumeAll()
    }

    def removeJob(String group, String name) {
        quartzScheduler.deleteJob(new JobKey(name, group))
    }

    def unscheduleJob(String group, String name) {
        quartzScheduler.unscheduleJobs(quartzScheduler.getTriggersOfJob(new JobKey(name, group))*.key)
    }

    def interruptJob(String group, String name) {
        quartzScheduler.interrupt(new JobKey(name, group))
    }
}
