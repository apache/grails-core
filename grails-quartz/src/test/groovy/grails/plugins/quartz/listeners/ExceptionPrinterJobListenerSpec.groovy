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

import org.quartz.JobDetail
import org.quartz.JobExecutionContext
import org.quartz.JobExecutionException
import spock.lang.Specification

class ExceptionPrinterJobListenerSpec extends Specification {

    ExceptionPrinterJobListener listener = new ExceptionPrinterJobListener()

    void 'the listener name is the historical bean name'() {
        expect:
        listener.name == 'exceptionPrinterListener'
        ExceptionPrinterJobListener.NAME == 'exceptionPrinterListener'
    }

    void 'a successful execution (no exception) is silently ignored'() {
        given:
        JobExecutionContext context = Stub(JobExecutionContext)

        when:
        listener.jobWasExecuted(context, null)

        then:
        noExceptionThrown()
    }

    void 'a job exception is logged with the job description'() {
        given:
        JobDetail detail = Stub(JobDetail) { getDescription() >> 'a scheduled task' }
        JobExecutionContext context = Stub(JobExecutionContext) { getJobDetail() >> detail }
        JobExecutionException exception = new JobExecutionException('boom')

        when:
        listener.jobWasExecuted(context, exception)

        then:
        noExceptionThrown()
    }

}
