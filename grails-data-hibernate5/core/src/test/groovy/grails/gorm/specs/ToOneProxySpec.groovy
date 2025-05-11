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
package grails.gorm.specs

import org.apache.grails.data.hibernate5.core.GrailsDataHibernate5TckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.grails.orm.hibernate.proxy.HibernateProxyHandler

/**
 * Created by graemerocher on 16/12/16.
 */
class ToOneProxySpec extends GrailsDataTckSpec<GrailsDataHibernate5TckManager> {
    void setupSpec() {
        manager.domainClasses.addAll([Team, Club])
    }

    void "test that a proxy is not initialized on get"() {
        given:
        Team t = new Team(name: "First Team", club: new Club(name: "Manchester United").save())
        t.save(flush:true)
        manager.session.clear()


        when:"An object is retrieved and the session is flushed"
        t = Team.get(t.id)
        manager.session.flush()

        def proxyHandler = new HibernateProxyHandler()
        then:"The association was not initialized"
        proxyHandler.getAssociationProxy(t, "club") != null
        !proxyHandler.isInitialized(t, "club")


    }
}
