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

package pubsub.demo

import grails.events.annotation.Subscriber
import groovy.transform.CompileStatic
import org.grails.datastore.mapping.engine.event.PreInsertEvent
import org.springframework.stereotype.Component

import java.util.concurrent.ConcurrentLinkedDeque

@Component
@CompileStatic
class BookSubscriber {

    List<String> newBooks = []

    @Subscriber('newBook')
    @SuppressWarnings('unused')
    void withBook(Book book) {
        newBooks.add(book.title)
    }

    // tag::gorm[]
    Collection<PreInsertEvent> insertEvents = new ConcurrentLinkedDeque<>()

    @Subscriber
    @SuppressWarnings('unused')
    void beforeInsert(PreInsertEvent event) {
        insertEvents.add(event)
    }
    // end::gorm[]
}
