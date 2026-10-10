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
package grails.gorm.rx.services

import java.lang.annotation.Documented
import java.lang.annotation.ElementType
import java.lang.annotation.Inherited
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

import org.codehaus.groovy.transform.GroovyASTTransformationClass
import spock.lang.Specification

import grails.gorm.rx.PersistentObservable
import grails.gorm.rx.collection.ObservableCollection
import grails.gorm.rx.collection.RxPersistentCollection
import grails.gorm.rx.collection.RxUnidirectionalCollection
import grails.gorm.rx.proxy.ObservableProxy
import org.apache.grails.common.compiler.GroovyTransformOrder
import org.grails.datastore.gorm.transform.GormASTTransformationClass
import org.grails.datastore.mapping.collection.PersistentCollection
import org.grails.datastore.mapping.proxy.EntityProxy
import org.grails.datastore.mapping.proxy.ProxyHandler
import org.grails.datastore.rx.proxy.ProxyFactory

class RxApiTypesSpec extends Specification {

    void "the rx schedule annotation is a source level method annotation wired to the ordered gorm transformation"() {
        expect:
        RxSchedule.getAnnotation(Retention).value() == RetentionPolicy.SOURCE
        RxSchedule.getAnnotation(Target).value() == [ElementType.METHOD] as ElementType[]
        RxSchedule.getAnnotation(Inherited) != null
        RxSchedule.getAnnotation(Documented) != null
        RxSchedule.getAnnotation(GroovyASTTransformationClass).value() == ['org.grails.datastore.gorm.transform.OrderedGormTransformation'] as String[]
        RxSchedule.getAnnotation(GormASTTransformationClass).value() == 'org.grails.gorm.rx.transform.RxScheduleIOTransformation'
        RxSchedule.getMethod('scheduler').defaultValue == Object
        RxSchedule.getMethod('singleResult').defaultValue == false
        RxSchedule.getMethod('priority').defaultValue == GroovyTransformOrder.RX_SCHEDULER_ORDER
    }

    void "the rx observable interfaces extend the datastore abstractions"() {
        expect:
        PersistentObservable.declaredMethods*.name.toSet() == ['toObservable', 'subscribe'] as Set
        PersistentObservable.isAssignableFrom(ObservableCollection)
        ObservableCollection.declaredMethods*.name == ['toListObservable']
        PersistentCollection.isAssignableFrom(RxPersistentCollection)
        ObservableCollection.isAssignableFrom(RxPersistentCollection)
        RxUnidirectionalCollection.declaredMethods*.name == ['getAssociationKeys']
        PersistentObservable.isAssignableFrom(ObservableProxy)
        EntityProxy.isAssignableFrom(ObservableProxy)
        ProxyHandler.isAssignableFrom(ProxyFactory)
        ProxyFactory.declaredMethods*.name.toSet() == ['createProxy'] as Set
        ProxyFactory.declaredMethods.length == 2
    }

}
