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
package grails.gorm.tests.events

import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import org.grails.datastore.gorm.events.ConfigurableApplicationEventPublisher
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEvent
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEventListener
import org.grails.datastore.mapping.engine.event.PreInsertEvent
import org.grails.datastore.mapping.engine.event.PreUpdateEvent
import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.Session
import org.hibernate.engine.spi.SessionImplementor
import org.springframework.context.ApplicationEvent
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by graemerocher on 05/04/2017.
 */
class UpdatePropertyInEventListenerSpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore hibernateDatastore = new HibernateDatastore(User)
    @Shared PlatformTransactionManager transactionManager = hibernateDatastore.transactionManager

    @Rollback
    void "Test that using an listener does not produce an extra update"() {
        given:
        ((ConfigurableApplicationEventPublisher)hibernateDatastore.applicationEventPublisher).addApplicationListener(
                new PasswordEncodingListener(hibernateDatastore)
        )
        Session session = hibernateDatastore.sessionFactory.currentSession

        when:"A user is inserted"
        User user = new User(username: "foo", password: "bar")
        user.save(flush:true)

        then:"The password is only encoded once and no update is issued"
        user.password == "xxxxxxxx0"

        when:"A user is found"
        session.clear()
        user = User.findByUsername("foo")
        session.flush()

        then:"The password is not encoded again"
        user.password == "xxxxxxxx0"

        when:"The user is updated"
        user.password = "blah"
        user.save(flush:true)

        then:"The password is encoded again"
        user.password == "xxxxxxxx1"

        when:"A user is found"
        session.clear()
        user = User.findByUsername("foo")
        session.flush()

        then:"The password is not encoded again"
        user.password == "xxxxxxxx1"
    }
}

@Entity
class User {
    String username
    String password

    static mapping = {
        table '`user`'
        password column: '`password`'
    }
}

class PasswordEncodingListener extends AbstractPersistenceEventListener {

    int i = 0

    PasswordEncodingListener(Datastore datastore) {
        super(datastore)
    }

    @Override
    protected void onPersistenceEvent(AbstractPersistenceEvent event) {
        event.getEntityAccess().setProperty("password", "xxxxxxxx${i++}".toString())
    }

    @Override
    boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
        return eventType == PreUpdateEvent || eventType == PreInsertEvent
    }
}
