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
package grails.gorm.specs.proxy

import groovy.transform.CompileStatic
import org.grails.orm.hibernate.proxy.HibernateProxyHandler
import org.hibernate.Hibernate
import grails.gorm.specs.entities.Team

@CompileStatic
class StaticTestUtil {
    public static HibernateProxyHandler proxyHandler = new HibernateProxyHandler()

    // should return true and not initialize the proxy
    // getId works inside a compile static
    static boolean team_id_asserts(Team team){
        assert team.getId()
        assert !Hibernate.isInitialized(team)
        assert proxyHandler.isProxy(team)

        assert team.id
        assert !Hibernate.isInitialized(team)
        assert proxyHandler.isProxy(team)
        //a truthy check on the object will try to init it because it hits the getMetaClass
        // assert team
        // assert !Hibernate.isInitialized(team)

        return true
    }

    static boolean club_id_asserts(Team team){
        assert team.club.getId()
        assert notInitialized(team.club)

        assert team.club.id
        assert notInitialized(team.club)

        assert team.clubId
        assert notInitialized(team.club)

        return true
    }

    static boolean notInitialized(Object o){
        //sanity check the 3
        assert !Hibernate.isInitialized(o)
        assert !proxyHandler.isInitialized(o)
        assert proxyHandler.isProxy(o)
        return true
    }
}

