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

import org.quartz.Job
import org.quartz.JobDetail
import org.quartz.JobExecutionContext
import org.quartz.JobExecutionException
import org.quartz.UnableToInterruptJobException
import org.quartz.impl.triggers.SimpleTriggerImpl
import org.quartz.spi.TriggerFiredBundle
import org.springframework.context.support.StaticApplicationContext
import spock.lang.Specification

/**
 * Tests the job the factory hands the scheduler for each execution of a job artefact.
 */
class GrailsJobFactorySpec extends Specification {

    private static final String JOB_NAME = 'grails.plugins.quartz.TestJob'
    private static final String JOB_GROUP = 'GRAILS_JOBS'

    void 'the job the factory creates runs the execute method of the job artefact'() {
        given:
            SimpleJob artefact = new SimpleJob()

        when:
            Job job = jobFor(artefact)
            job.execute(Mock(JobExecutionContext))

        then:
            artefact.executions == 1
    }

    void 'a job artefact whose execute method takes the execution context is given it'() {
        given:
            ContextAwareJob artefact = new ContextAwareJob()
            JobExecutionContext context = Mock(JobExecutionContext)

        when:
            jobFor(artefact).execute(context)

        then:
            artefact.context.is(context)
    }

    void 'a job artefact without an execute method is rejected'() {
        when:
            new GrailsJobFactory.GrailsJob(new NoExecuteJob())

        then:
            IllegalArgumentException e = thrown()
            e.message.contains(NoExecuteJob.name)
            e.message.contains('execute')
    }

    void 'a job artefact whose execute method takes more than one argument is rejected'() {
        when:
            new GrailsJobFactory.GrailsJob(new TooManyArgumentsJob())

        then:
            IllegalArgumentException e = thrown()
            e.message.contains(TooManyArgumentsJob.name)
    }

    void 'an exception thrown by a job artefact is reported as a job execution exception'() {
        when:
            jobFor(new FailingJob()).execute(Mock(JobExecutionContext))

        then:
            JobExecutionException e = thrown()
            e.cause instanceof IllegalStateException
            e.cause.message == 'the job failed'
    }

    void 'a job execution exception thrown by a job artefact is reported as it is'() {
        given:
            JobExecutionException thrownByJob = new JobExecutionException('unschedule me')

        when:
            jobFor(new JobExecutionExceptionJob(exception: thrownByJob)).execute(Mock(JobExecutionContext))

        then:
            JobExecutionException e = thrown()
            e.is(thrownByJob)
    }

    void 'interrupting a job calls the interrupt method of the job artefact'() {
        given:
            InterruptibleJob artefact = new InterruptibleJob()
            Job job = jobFor(artefact)

        when:
            job.interrupt()

        then:
            artefact.interrupted
    }

    void 'interrupting a job artefact which does not declare an interrupt method reports that it cannot be interrupted'() {
        given:
            Job job = jobFor(new SimpleJob())

        when:
            job.interrupt()

        then:
            UnableToInterruptJobException e = thrown()
            e.message == "${SimpleJob.name} does not declare an interrupt() method, so it cannot be interrupted"
    }

    /**
     * Builds the job the way the scheduler does: through the factory, which looks the job artefact up
     * in the application context by the name the job detail carries.
     */
    private Job jobFor(Object artefact) {
        StaticApplicationContext applicationContext = new StaticApplicationContext()
        applicationContext.beanFactory.registerSingleton(JOB_NAME, artefact)
        applicationContext.refresh()

        GrailsJobFactory factory = new GrailsJobFactory()
        factory.applicationContext = applicationContext

        factory.newJob(new TriggerFiredBundle(jobDetail(), new SimpleTriggerImpl('trigger', 'GRAILS_TRIGGERS'),
                null, false, new Date(), null, null, null), null)
    }

    private JobDetail jobDetail() {
        JobDetailFactoryBean factory = new JobDetailFactoryBean()
        factory.jobClass = new GrailsJobClassMock(fullName: JOB_NAME, group: JOB_GROUP, concurrent: true)
        factory.afterPropertiesSet()
        factory.object
    }
}

class SimpleJob {
    int executions

    void execute() {
        executions++
    }
}

class ContextAwareJob {
    JobExecutionContext context

    void execute(JobExecutionContext context) {
        this.context = context
    }
}

class NoExecuteJob {
}

class TooManyArgumentsJob {
    void execute(JobExecutionContext context, String other) {}
}

class FailingJob {
    void execute() {
        throw new IllegalStateException('the job failed')
    }
}

class JobExecutionExceptionJob {
    JobExecutionException exception

    void execute() {
        throw exception
    }
}

class InterruptibleJob {
    boolean interrupted

    void execute() {}

    void interrupt() {
        interrupted = true
    }
}
