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
package org.grails.compiler.gorm

import org.grails.datastore.gorm.GormEntity
import org.grails.datastore.mapping.model.config.GormProperties
import org.grails.datastore.mapping.reflect.ClassPropertyFetcher
import org.springframework.validation.annotation.Validated
import spock.lang.Specification

import jakarta.persistence.Transient


/**
 * Created by graemerocher on 22/12/16.
 */
class JpaEntityTransformSpec extends Specification {

    void "test the JPA entity transform the entity correctly"() {
        given:
        GroovyClassLoader gcl = new GroovyClassLoader()
        Class customerClass = gcl.parseClass('''
import jakarta.persistence.*
import jakarta.validation.constraints.Digits
@Entity
class Customer {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    Long myId
    @Digits
    String firstName;
    String lastName;
    
    @jakarta.persistence.OneToMany
    Set<Customer> related

}

''')
        ClassPropertyFetcher cpf = ClassPropertyFetcher.forClass(customerClass)
        def instance = customerClass.newInstance()
        instance.myId = 1L
        expect:
        instance.id == 1L
        GormEntity.isAssignableFrom(customerClass)
        customerClass.getAnnotation(Validated)
        customerClass.getDeclaredMethod("getId").returnType == Long
        customerClass.getDeclaredMethod("getId").getAnnotation(Transient)
        cpf.getPropertyDescriptor(GormProperties.IDENTITY)
        customerClass.getDeclaredMethod('addToRelated', Object)
        customerClass.getDeclaredMethod('removeFromRelated', Object)
    }
}

