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
package org.grails.core.util

import spock.lang.Specification

class StopWatchSpec extends Specification {

    void 'not running before any task is started'() {
        given:
        def stopWatch = new StopWatch('my watch')

        expect:
        !stopWatch.running
        stopWatch.taskCount == 0
        stopWatch.totalTimeMillis == 0
    }

    void 'start and stop records a task'() {
        given:
        def stopWatch = new StopWatch()

        when:
        stopWatch.start('task-1')

        then:
        stopWatch.running
        stopWatch.taskCount == 1

        when:
        stopWatch.stop()

        then:
        stopWatch.lastTaskName == 'task-1'
        stopWatch.lastTaskInfo.taskName == 'task-1'
        stopWatch.totalTimeMillis >= 0
        stopWatch.totalTimeSeconds == stopWatch.totalTimeMillis / 1000.0
        stopWatch.taskInfo.length == 1
    }

    void 'nested tasks are both tracked and totalled'() {
        given:
        def stopWatch = new StopWatch()

        when:
        stopWatch.start('outer')
        stopWatch.start('inner')
        stopWatch.stop()
        stopWatch.stop()

        then:
        stopWatch.taskCount == 2
        stopWatch.taskInfo.length == 2
        // lastTaskInfo tracks the most recently *started* task, not the most recently stopped one
        stopWatch.lastTaskName == 'inner'
    }

    void 'stop without a running task throws IllegalStateException'() {
        given:
        def stopWatch = new StopWatch()

        when:
        stopWatch.stop()

        then:
        thrown(IllegalStateException)
    }

    void 'querying the last task before any task ran throws IllegalStateException'() {
        given:
        def stopWatch = new StopWatch()

        when:
        stopWatch.lastTaskTimeMillis

        then:
        thrown(IllegalStateException)

        when:
        stopWatch.lastTaskName

        then:
        thrown(IllegalStateException)

        when:
        stopWatch.lastTaskInfo

        then:
        thrown(IllegalStateException)
    }

    void 'complete stops the watch running without recording a task'() {
        given:
        def stopWatch = new StopWatch()
        stopWatch.start('a task')

        when:
        stopWatch.complete()

        then:
        !stopWatch.running
    }

    void 'shortSummary, prettyPrint and toString describe the recorded tasks'() {
        given:
        def stopWatch = new StopWatch('id')
        stopWatch.start('task-a')
        Thread.sleep(5)
        stopWatch.stop()

        expect:
        stopWatch.shortSummary().contains("StopWatch 'id'")
        stopWatch.prettyPrint().contains('task-a')
        stopWatch.toString().contains('task-a')
    }
}
