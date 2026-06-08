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

package com.test

import com.unboundid.ldap.listener.InMemoryDirectoryServer
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig
import com.unboundid.ldap.listener.InMemoryListenerConfig
import com.unboundid.ldap.sdk.Attribute
import com.unboundid.ldap.sdk.Entry
import grails.boot.GrailsApp
import grails.boot.config.GrailsAutoConfiguration

import groovy.transform.CompileStatic

@CompileStatic
class Application extends GrailsAutoConfiguration {
    static InMemoryDirectoryServer directoryServer
    static private Entry scientistsUnit

    static void main(String[] args) {
        InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig('dc=example,dc=com')
        config.addAdditionalBindCredentials('cn=admin,dc=example,dc=com', 'secret')
        config.setListenerConfigs(
                InMemoryListenerConfig.createLDAPConfig(
                        'default',
                        null,
                        0,
                        null,
                        false,
                        false
                )
        )

        directoryServer = new InMemoryDirectoryServer(config)
        Entry base = new Entry(
                'dc=example,dc=com',
                new Attribute('objectClass', 'top', 'domain'),
                new Attribute('dc', 'example'))
        directoryServer.add(base)

        Entry people = new Entry(
                'ou=people,dc=example,dc=com',
                new Attribute('objectClass', 'top', 'organizationalUnit'),
                new Attribute('ou', 'people'));
        directoryServer.add(people)

        def mathematiciansUnit = new Entry('ou=mathematicians,dc=example,dc=com',
                new Attribute('objectClass', 'top', 'organizationalUnit'),
                new Attribute('ou', 'mathematicians'))
        directoryServer.add(mathematiciansUnit)

        scientistsUnit = new Entry('ou=scientists,dc=example,dc=com',
                new Attribute('objectClass', 'top', 'organizationalUnit'),
                new Attribute('ou', 'scientists'))
        directoryServer.add(scientistsUnit)

        Entry jane = new Entry(
                'uid=jane,ou=people,dc=example,dc=com',
                new Attribute('objectClass', 'inetOrgPerson'),
                new Attribute('uid', 'jane'),
                new Attribute('cn', 'Jane Doe'),
                new Attribute('sn', 'Doe'),
                new Attribute('mail', 'jane@example.com'),
                new Attribute('telephoneNumber', '+1 555 111 2222'),
                new Attribute('userPassword', 'password')
        )
        directoryServer.add(jane)

        for (String uid : ['riemann', 'gauss', 'euler', 'euclid']) {
            directoryServer.add(new Entry(
                    "uid=${uid},ou=mathematicians,dc=example,dc=com" as String,
                    new Attribute('objectClass', 'inetOrgPerson'),
                    new Attribute('uid', uid),
                    new Attribute('cn', uid.substring(0, 1).toUpperCase() + uid.substring(1)),
                    new Attribute('sn', uid.substring(0, 1).toUpperCase() + uid.substring(1)),
                    new Attribute('userPassword', 'password')
            ))
        }

        Entry mathGroup = new Entry(
                'cn=mathematicians,ou=mathematicians,dc=example,dc=com',
                new Attribute('objectClass', 'top', 'groupOfUniqueNames'),
                new Attribute('cn', 'mathematicians'),
                new Attribute('uniqueMember',
                        'uid=riemann,ou=mathematicians,dc=example,dc=com',
                        'uid=gauss,ou=mathematicians,dc=example,dc=com',
                        'uid=euler,ou=mathematicians,dc=example,dc=com',
                        'uid=euclid,ou=mathematicians,dc=example,dc=com'))
        directoryServer.add(mathGroup)

        for (String uid : ['einstein', 'newton', 'galieleo', 'tesla']) {
            directoryServer.add(new Entry(
                    "uid=${uid},ou=scientists,dc=example,dc=com" as String,
                    new Attribute('objectClass', 'inetOrgPerson'),
                    new Attribute('uid', uid),
                    new Attribute('cn', uid.substring(0, 1).toUpperCase() + uid.substring(1)),
                    new Attribute('sn', uid.substring(0, 1).toUpperCase() + uid.substring(1)),
                    new Attribute('userPassword', 'password')
            ))
        }
        Entry scientistGroup = new Entry(
                'cn=scientists,ou=scientists,dc=example,dc=com',
                new Attribute('objectClass', 'top', 'groupOfUniqueNames'),
                new Attribute('cn', 'scientists'),
                new Attribute('uniqueMember',
                        'uid=einstein,ou=scientists,dc=example,dc=com',
                        'uid=newton,ou=scientists,dc=example,dc=com',
                        'uid=galieleo,ou=scientists,dc=example,dc=com',
                        'uid=tesla,ou=scientists,dc=example,dc=com'))
        directoryServer.add(scientistGroup)

        directoryServer.startListening()

        System.setProperty('grails.test.ldap.url', "ldap://localhost:${directoryServer.getListenPort()}" as String)

        GrailsApp.run(Application, args)
    }

    @Override
    void onShutdown(Map<String, Object> event) {
        if (directoryServer) {
            directoryServer.close()
            directoryServer = null
        }
    }
}