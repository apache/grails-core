/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.grails.data.testing.tck.tests

import org.apache.grails.data.testing.tck.domains.Task
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec

/**
 * @author graemerocher
 */
class CircularOneToManySpec extends GrailsDataTckSpec {

    void setupSpec() {
        manager.domainClasses.add(Task)
    }

    void "Test circular one-to-many"() {
        given:
        def parent = new Task(name: "Root").save()
        def child = new Task(task: parent, name: "Finish Job").save(flush: true)
        manager.session.clear()

        when:
        parent = Task.findByName("Root")
        child = Task.findByName("Finish Job")

        then:
        parent.task == null
        child.task.id == parent.id
    }
}