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
package org.grails.datastore.gorm

import org.apache.grails.data.testing.tck.domains.TestEntity
import org.apache.grails.data.simple.core.GrailsDataCoreTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec

/**
 * Created by graemerocher on 22/08/2017.
 */
class NotLikeSpec extends GrailsDataTckSpec<GrailsDataCoreTckManager> {

    void "test not like"() {
        when:
        new TestEntity(name:"Fred").save()
        new TestEntity(name:"Frank").save()
        new TestEntity(name:"Jack").save(flush:true)

        then:
        TestEntity.countByNameNotLike("F%") == 1
        TestEntity.findByNameNotLike("F%").name == "Jack"
        TestEntity.findAllByNameNotLike("J%").size() == 2
    }
}

