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
package grails.plugins.quartz.listeners

import org.quartz.JobExecutionContext
import org.quartz.JobExecutionException
import spock.lang.Specification

import grails.persistence.support.PersistenceContextInterceptor

class SessionBinderJobListenerSpec extends Specification {

    SessionBinderJobListener listener = new SessionBinderJobListener()

    void 'the listener name is the historical bean name and the interceptor is a settable bean property'() {
        expect:
        listener.name == 'sessionBinderListener'
        SessionBinderJobListener.NAME == 'sessionBinderListener'
        listener.persistenceInterceptor == null

        when:
        PersistenceContextInterceptor interceptor = Mock(PersistenceContextInterceptor)
        listener.persistenceInterceptor = interceptor

        then:
        listener.persistenceInterceptor.is(interceptor)
    }

    void 'jobToBeExecuted initializes the persistence context when an interceptor is set'() {
        given:
        PersistenceContextInterceptor interceptor = Mock(PersistenceContextInterceptor)
        listener.persistenceInterceptor = interceptor

        when:
        listener.jobToBeExecuted(Stub(JobExecutionContext))

        then:
        1 * interceptor.init()
    }

    void 'jobToBeExecuted is a no-op without an interceptor'() {
        when:
        listener.jobToBeExecuted(Stub(JobExecutionContext))

        then:
        noExceptionThrown()
    }

    void 'jobWasExecuted flushes, clears and destroys the persistence context'() {
        given:
        PersistenceContextInterceptor interceptor = Mock(PersistenceContextInterceptor)
        listener.persistenceInterceptor = interceptor

        when:
        listener.jobWasExecuted(Stub(JobExecutionContext), null)

        then:
        1 * interceptor.flush()
        1 * interceptor.clear()
        1 * interceptor.destroy()
    }

    void 'a failure to flush still destroys the persistence context'() {
        given:
        PersistenceContextInterceptor interceptor = Mock(PersistenceContextInterceptor)
        listener.persistenceInterceptor = interceptor
        interceptor.flush() >> { throw new IllegalStateException('flush failed') }
        org.quartz.JobDetail detail = Stub(org.quartz.JobDetail) { getDescription() >> 'a job' }
        JobExecutionContext context = Stub(JobExecutionContext) { getJobDetail() >> detail }

        when:
        listener.jobWasExecuted(context, new JobExecutionException('job failed'))

        then:
        noExceptionThrown()
        1 * interceptor.destroy()
    }

    void 'jobWasExecuted is a no-op without an interceptor'() {
        when:
        listener.jobWasExecuted(Stub(JobExecutionContext), null)

        then:
        noExceptionThrown()
    }

}
