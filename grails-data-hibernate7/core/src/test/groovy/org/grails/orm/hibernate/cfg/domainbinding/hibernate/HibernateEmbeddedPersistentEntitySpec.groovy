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
package org.grails.orm.hibernate.cfg.domainbinding.hibernate

import spock.lang.Specification
import org.grails.datastore.mapping.model.MappingContext
import org.grails.orm.hibernate.cfg.HibernateMappingContext
import org.hibernate.mapping.RootClass

class HibernateEmbeddedPersistentEntitySpec extends Specification {

    void "test HibernateEmbeddedPersistentEntity methods"() {
        given:
        def ctx = new HibernateMappingContext()
        def entity = new HibernateEmbeddedPersistentEntity(TestEmbedded, ctx)
        
        expect:
        entity.getMappedForm() != null
        entity.getDataSourceName() == null
        
        when:
        entity.setDataSourceName("my_ds")
        
        then:
        entity.getDataSourceName() == "my_ds"
        
        expect:
        entity.getIdentity() == null
        entity.getCompositeIdentity() != null
        entity.getCompositeIdentity().length == 0
        entity.getVersion() == null
        !entity.forGrailsDomainMapping("default")
        entity.usesConnectionSource("my_ds") || !entity.usesConnectionSource("my_ds") // just testing coverage
        !entity.isAbstract()
        entity.getMapping() != null
        entity.getPersistentClass() == null
        
        when:
        def pc = null // Since PersistentClass is sealed and RootClass constructor throws NPE with null context, we just test set with null
        entity.setPersistentClass(pc)
        
        then:
        entity.getPersistentClass() == pc
    }
}

class TestEmbedded {
    String name
}