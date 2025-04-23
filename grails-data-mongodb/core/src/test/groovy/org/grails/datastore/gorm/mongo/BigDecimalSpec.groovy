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

import grails.gorm.annotation.Entity
import grails.gorm.tests.GormDatastoreSpec
import grails.mongodb.MongoEntity
import org.bson.Document
import org.bson.types.Decimal128
import spock.lang.Ignore
import spock.lang.IgnoreIf

/**
 * Created by graemerocher on 14/12/16.
 */
class BigDecimalSpec extends GormDatastoreSpec {

    void "test save and retrieve big decimal value"() {
        when:"A big decimal is saved"
        def val = new BigDecimal("1.0")
        new BossMan(salary: val).save(flush:true)
        session.clear()
        BossMan bm = BossMan.first()
        then:""
        bm.salary == val
        BossMan.collection.find().first().salary instanceof Decimal128

    }

    @Override
    List getDomainClasses() {
        [BossMan]
    }
}
@Entity
class BossMan implements MongoEntity<BossMan> {
    BigDecimal salary
}
