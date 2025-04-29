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
package org.grails.datastore.gorm.mongo

import grails.gorm.dirty.checking.DirtyCheck
import grails.persistence.Entity
import org.apache.grails.data.mongo.core.GrailsDataMongoTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.bson.types.ObjectId
import org.grails.datastore.mapping.mongo.config.MongoSettings

class MarkDirtyFalseSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {

    void setupSpec() {
        manager.domainClasses.addAll([Bar, BarWithTimestamp])
        manager.configuration.putAll([(MongoSettings.SETTING_MARK_DIRTY): false])
    }

    void "test behavior with mark dirty false"() {
        when:
        def b = new Bar(foo:"stuff", strings:['a', 'b'])
        b.save(flush:true)
        manager.session.clear()
        b = Bar.get(b.id)
        b.save(flush: true)

        then:
        b.version == 0

        when:
        def bTs = new BarWithTimestamp(foo:"stuff")
        bTs.save(flush:true)
        manager.session.clear()
        bTs = BarWithTimestamp.get(bTs.id)
        bTs.save(flush: true)

        then:
        bTs.version == 0
    }
}

@Entity
@DirtyCheck
class BarWithTimestamp {
    ObjectId id

    String foo

    Date lastUpdated

}
