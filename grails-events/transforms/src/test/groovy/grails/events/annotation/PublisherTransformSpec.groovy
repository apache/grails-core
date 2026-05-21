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
package grails.events.annotation

import org.grails.datastore.gorm.transactions.transform.TransactionalTransform
import org.grails.datastore.mapping.core.order.OrderedComparator
import org.grails.events.transform.PublisherTransform
import spock.lang.Specification

/**
 * Created by graemerocher on 30/03/2017.
 */
class PublisherTransformSpec extends Specification {

    void 'Test order'() {

        given: 'a list of transforms'
            def list = [new PublisherTransform(), new TransactionalTransform()]

        when: 'we sort the list'
            Collections.sort(list, new OrderedComparator<>())

        then: 'the transactional transform is first'
            list.first() instanceof TransactionalTransform
    }
}
