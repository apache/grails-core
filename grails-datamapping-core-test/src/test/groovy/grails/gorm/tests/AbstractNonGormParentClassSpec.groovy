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

package grails.gorm.tests

import grails.persistence.Entity

/**
 * Created by graemerocher on 25/02/16.
 */
class AbstractNonGormParentClassSpec extends GormDatastoreSpec {

    void "Test a concrete domain class that extends a common base class"() {
        expect:
        session.mappingContext.getPersistentEntity(ConcreteFoo.name)
        !session.mappingContext.getPersistentEntity(Common.name)

    }
    @Override
    List getDomainClasses() {
        [ConcreteFoo]
    }
}

abstract class Common {
    String foo
}

@Entity
class ConcreteFoo extends Common {
    String bar
}
