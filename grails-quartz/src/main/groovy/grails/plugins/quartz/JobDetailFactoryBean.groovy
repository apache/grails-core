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
import org.quartz.JobBuilder
import org.quartz.JobDetail
import org.springframework.beans.factory.FactoryBean
import org.springframework.beans.factory.InitializingBean

import static org.quartz.JobBuilder.newJob

/**
 * Simplified version of Spring's <a href='http://static.springframework.org/spring/docs/2.5.x/api/org/springframework/scheduling/quartz/MethodInvokingJobDetailFactoryBean.html'>MethodInvokingJobDetailFactoryBean</a>
 * that avoids issues with non-serializable classes (for JDBC storage).
 *
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 * @author Sergey Nebolsin (nebolsin@gmail.com)
 * @since 0.3.2
 */
@CompileStatic
class JobDetailFactoryBean implements FactoryBean<JobDetail>, InitializingBean {

    public static final transient String JOB_NAME_PARAMETER = 'org.grails.plugins.quartz.grailsJobName'

    /**
     * The job data entry naming the application a job was registered by. It is what tells the jobs of one
     * application apart from the jobs of another when both share a job store.
     */
    public static final transient String APPLICATION_NAME_PARAMETER = 'org.apache.grails.quartz.applicationName'

    // Properties
    private GrailsJobClass jobClass
    private String applicationName

    // Returned object
    private JobDetail jobDetail

    void setJobClass(GrailsJobClass jobClass) {
        this.jobClass = jobClass
    }

    /**
     * Sets the name of the application the job belongs to. The job carries it as job data, so that the
     * application can recognise its own jobs in a job store it shares with other applications.
     *
     * @param applicationName the name of the application registering the job
     */
    void setApplicationName(String applicationName) {
        this.applicationName = applicationName
    }

    /**
     * {@inheritDoc}
     *
     * @see org.springframework.beans.factory.InitializingBean#afterPropertiesSet()
     */
    void afterPropertiesSet() {
        String name = jobClass.getFullName()
        if (name == null) {
            throw new IllegalStateException('name is required')
        }

        String group = jobClass.getGroup()
        if (group == null) {
            throw new IllegalStateException('group is required')
        }

        // Consider the concurrent flag to choose between stateful and stateless job.
        Class<? extends GrailsJobFactory.GrailsJob> clazz =
                jobClass.isConcurrent() ? GrailsJobFactory.GrailsJob : GrailsJobFactory.StatefulGrailsJob

        // Build JobDetail instance.
        JobBuilder builder =
                newJob(clazz)
                        .withIdentity(name, group)
                        .storeDurably(jobClass.isDurability())
                        .requestRecovery(jobClass.isRequestsRecovery())
                        .usingJobData(JOB_NAME_PARAMETER, name)
                        .withDescription(jobClass.getDescription())

        if (applicationName != null) {
            builder.usingJobData(APPLICATION_NAME_PARAMETER, applicationName)
        }

        jobDetail = builder.build()
    }

    /**
     * {@inheritDoc}
     *
     * @see org.springframework.beans.factory.FactoryBean#getObject()
     */
    @Override
    JobDetail getObject() {
        return jobDetail
    }

    /**
     * {@inheritDoc}
     *
     * @see org.springframework.beans.factory.FactoryBean#getObjectType()
     */
    @Override
    Class<JobDetail> getObjectType() {
        return JobDetail
    }

    /**
     * {@inheritDoc}
     *
     * @see org.springframework.beans.factory.FactoryBean#isSingleton()
     */
    @Override
    boolean isSingleton() {
        return true
    }

}
