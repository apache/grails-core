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
package grails.plugin.springsecurity.acl

import spock.lang.Issue
import spock.lang.Specification

/**
 * The domain classes that {@code s2-create-acl-domains} copies into an application are rendered from the templates of the
 * plugin. They have to compare and serialize as the domain classes of the plugin do, because the ACL services look ACLs
 * up by the equality of these objects: a copy that compares by identity does not find an ACL it has just read, and the
 * lookup fails with a NotFoundException for an object whose ACL exists.
 */
@Issue('#15927')
class GeneratedAclDomainsSpec extends Specification {

    private static final String TEMPLATES = 'src/main/templates'

    /**
     * The class the template renders, compiled under another name so that it does not clash with the class of the plugin.
     */
    private Class generated(String name) {
        String source = new File(TEMPLATES, "_${name}.groovy").text.replace('\\$', '$')
        assert source.contains("class ${name}")
        new GroovyClassLoader(getClass().classLoader).parseClass(source.replace("class ${name}", "class Generated${name}"), "Generated${name}.groovy")
    }

    void "a generated AclSid is equal to another that has the same sid and principal"() {
        given:
        Class sid = generated('AclSid')

        expect:
        sid.newInstance(sid: 'ROLE_ADMIN', principal: false) == sid.newInstance(sid: 'ROLE_ADMIN', principal: false)
        sid.newInstance(sid: 'ROLE_ADMIN', principal: false).hashCode() == sid.newInstance(sid: 'ROLE_ADMIN', principal: false).hashCode()
        sid.newInstance(sid: 'ROLE_ADMIN', principal: false) != sid.newInstance(sid: 'ROLE_USER', principal: false)
        sid.newInstance(sid: 'admin', principal: true) != sid.newInstance(sid: 'admin', principal: false)
        Serializable.isAssignableFrom(sid)
    }

    void "a generated AclClass is equal to another that has the same class name"() {
        given:
        Class aclClass = generated('AclClass')

        expect:
        aclClass.newInstance(className: 'com.example.Book') == aclClass.newInstance(className: 'com.example.Book')
        aclClass.newInstance(className: 'com.example.Book').hashCode() == aclClass.newInstance(className: 'com.example.Book').hashCode()
        aclClass.newInstance(className: 'com.example.Book') != aclClass.newInstance(className: 'com.example.Author')
        Serializable.isAssignableFrom(aclClass)
    }

    void "a generated AclEntry is equal to another that has the same identity, order, sid and permissions"() {
        given:
        Class entry = generated('AclEntry')
        AclObjectIdentity identity = new AclObjectIdentity(objectId: 1L)
        AclSid sid = new AclSid(sid: 'ROLE_ADMIN', principal: false)
        Map values = [aclObjectIdentity: identity, aceOrder: 0, sid: sid, mask: 1, granting: true, auditSuccess: false, auditFailure: false]

        expect:
        entry.newInstance(values) == entry.newInstance(values)
        entry.newInstance(values).hashCode() == entry.newInstance(values).hashCode()
        entry.newInstance(values) != entry.newInstance(values + [mask: 2])
        entry.newInstance(values) != entry.newInstance(values + [aceOrder: 1])
        entry.newInstance(values) != entry.newInstance(values + [granting: false])
        Serializable.isAssignableFrom(entry)
    }

    void "a generated AclObjectIdentity is equal to another that has the same object id and class"() {
        given:
        Class identity = generated('AclObjectIdentity')
        AclClass aclClass = new AclClass(className: 'com.example.Book')

        expect:
        identity.newInstance(objectId: 2L, aclClass: aclClass) == identity.newInstance(objectId: 2L, aclClass: aclClass)
        identity.newInstance(objectId: 2L, aclClass: aclClass).hashCode() == identity.newInstance(objectId: 2L, aclClass: aclClass).hashCode()
        identity.newInstance(objectId: 2L, aclClass: aclClass) != identity.newInstance(objectId: 3L, aclClass: aclClass)
        Serializable.isAssignableFrom(identity)
    }
}
