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

package grails.boot

import spock.lang.Specification

import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup
import org.springframework.boot.context.metrics.buffering.StartupTimeline
import org.springframework.context.ApplicationContext
import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.metrics.ApplicationStartup
import org.springframework.core.metrics.StartupStep

class StartupTaskSpec extends Specification {

    void 'a task and its items are recorded as steps of the application startup'() {
        given: 'a context whose start is recorded'
        BufferingApplicationStartup recorder = new BufferingApplicationStartup(100)
        GenericApplicationContext context = new GenericApplicationContext()
        context.applicationStartup = recorder

        when: 'a task reports two items'
        StartupTask task = StartupTask.start(context, 'Loading reference data', 2)
        task.startItem('countries')
        task.endItem()
        task.startItem('currencies')
        task.close()

        then: 'each item is a step of its own, ended before the task'
        List<StartupStep> steps = recorder.bufferedTimeline.events*.startupStep
        steps*.name == [StartupTask.ITEM_STEP, StartupTask.ITEM_STEP, StartupTask.TASK_STEP]
        tags(steps[0]) == [(StartupTask.ITEM_TAG): 'countries']
        tags(steps[1]) == [(StartupTask.ITEM_TAG): 'currencies']

        and: 'the task says what it does and how many items it does'
        tags(steps[2]) == [(StartupTask.DESCRIPTION_TAG): 'Loading reference data', (StartupTask.TOTAL_TAG): '2']

        and: 'the items belong to the task'
        steps[0].parentId == steps[2].id
        steps[1].parentId == steps[2].id
    }

    void 'starting an item ends the one before it'() {
        given:
        BufferingApplicationStartup recorder = new BufferingApplicationStartup(100)

        when: 'items are begun without being ended'
        StartupTask task = StartupTask.start(recorder, 'Warming caches', 2)
        task.startItem('products')
        task.startItem('prices')
        task.close()

        then: 'each is ended when the next begins, and the last when the task ends'
        recorder.bufferedTimeline.events*.startupStep*.name == [StartupTask.ITEM_STEP, StartupTask.ITEM_STEP, StartupTask.TASK_STEP]
    }

    void 'a task with no known number of items does not say how many it does'() {
        given:
        BufferingApplicationStartup recorder = new BufferingApplicationStartup(100)

        when:
        StartupTask.start(recorder, 'Indexing documents', -1).close()

        then:
        tags(recorder.bufferedTimeline.events.first().startupStep) == [(StartupTask.DESCRIPTION_TAG): 'Indexing documents']
    }

    void 'a task tells whether anything records the start'() {
        given:
        GenericApplicationContext recorded = new GenericApplicationContext()
        recorded.applicationStartup = new BufferingApplicationStartup(100)

        expect: 'a context with a recording application startup is recorded'
        StartupTask.isRecorded(recorded)

        and: 'one left with the default, which records nothing, is not'
        !StartupTask.isRecorded(new GenericApplicationContext())

        and: 'nor is a context whose application startup cannot be known'
        !StartupTask.isRecorded(Stub(ApplicationContext))
    }

    void 'a task reported to a context that records nothing costs nothing and does not fail'() {
        given:
        GenericApplicationContext context = new GenericApplicationContext()
        assert context.applicationStartup == ApplicationStartup.DEFAULT

        when:
        StartupTask task = StartupTask.start(context, 'Loading reference data', 1)
        task.startItem('countries')
        task.close()

        then:
        noExceptionThrown()
    }

    private static Map<String, String> tags(StartupStep step) {
        step.tags.collectEntries { StartupStep.Tag tag -> [(tag.key): tag.value] }
    }
}
