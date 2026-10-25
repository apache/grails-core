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

    void 'new watches have no tasks or elapsed time'() {
        given:
        def watch = new StopWatch()

        expect:
        !watch.isRunning()
        watch.getTaskCount() == 0
        watch.getTaskInfo().length == 0
        watch.getTotalTimeMillis() == 0
        watch.getTotalTimeSeconds() == 0.0
        watch.shortSummary() == "StopWatch '': running time (millis) = 0"
        watch.prettyPrint().contains('Task name')
        watch.toString() == watch.shortSummary()
    }

    void 'start and stop record an unnamed task and complete ends the watch'() {
        given:
        def watch = new StopWatch('startup')

        when:
        watch.start()

        then:
        watch.isRunning()
        watch.getTaskCount() == 1
        watch.getLastTaskName() == ''

        when:
        watch.stop()
        watch.complete()

        then:
        !watch.isRunning()
        watch.getTaskInfo().length == 1
        watch.getLastTaskTimeMillis() >= 0
        watch.getLastTaskInfo().is(watch.getTaskInfo()[0])
        watch.getTotalTimeMillis() == watch.getLastTaskTimeMillis()
        watch.getTotalTimeSeconds() == watch.getTotalTimeMillis() / 1000.0
        watch.getLastTaskInfo().getTimeSeconds() == watch.getLastTaskTimeMillis() / 1000.0
    }

    void 'multiple named tasks contribute to totals and reports'() {
        given:
        def watch = new StopWatch('startup')

        when:
        watch.start('configuration')
        watch.stop()
        watch.start('beans')
        watch.stop()
        watch.complete()

        then:
        watch.getTaskCount() == 2
        watch.getTaskInfo()*.taskName == ['configuration', 'beans']
        watch.getLastTaskName() == 'beans'
        watch.getTotalTimeMillis() == watch.getTaskInfo()*.timeMillis.sum()
        watch.getTaskInfo().every { it.timeMillis >= 0 }
        watch.shortSummary() == "StopWatch 'startup': running time (millis) = ${watch.getTotalTimeMillis()}"
        watch.prettyPrint().startsWith(watch.shortSummary())
        watch.prettyPrint().contains('Task name')
        watch.prettyPrint().readLines().any { it.endsWith('configuration') }
        watch.prettyPrint().readLines().any { it.endsWith('beans') }
        watch.toString().contains('[configuration] took ')
        watch.toString().contains('[beans] took ')
    }

    void 'starting twice supports nested tasks stopped in reverse order'() {
        given:
        def watch = new StopWatch('nested')

        when:
        watch.start('outer')
        watch.start('inner')
        watch.stop()
        watch.stop()
        watch.complete()

        then:
        watch.getTaskCount() == 2
        watch.getTaskInfo()*.taskName == ['inner', 'outer']
        watch.getTaskInfo()[1].timeMillis >= watch.getTaskInfo()[0].timeMillis
        watch.getTotalTimeMillis() == watch.getTaskInfo()*.timeMillis.sum()
        !watch.isRunning()
    }

    void 'stopping without starting throws'() {
        when:
        new StopWatch().stop()

        then:
        thrown(IllegalStateException)
    }

    void 'last task accessors throw before any task is started'() {
        given:
        def watch = new StopWatch()

        when:
        operation(watch)

        then:
        thrown(IllegalStateException)

        where:
        operation << [
            { it.getLastTaskName() },
            { it.getLastTaskTimeMillis() },
            { it.getLastTaskInfo() }
        ]
    }

    void 'completed watches reject stop'() {
        given:
        def watch = new StopWatch()
        watch.start('task')
        watch.stop()
        watch.complete()

        when:
        watch.stop()

        then:
        thrown(IllegalStateException)
    }
}
