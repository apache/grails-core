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
package org.grails.orm.hibernate.compiler

import groovy.transform.Generated
import org.hibernate.engine.spi.EntityEntry
import org.hibernate.engine.spi.ManagedEntity
import org.hibernate.engine.spi.PersistentAttributeInterceptable
import org.hibernate.engine.spi.PersistentAttributeInterceptor
import spock.lang.Specification

/**
 * Created by graemerocher on 15/11/16.
 */
class HibernateEntityTransformationSpec extends Specification {

    void "test hibernate entity transformation"() {
        when:"A hibernate interceptor is set"
        Class cls = new GroovyClassLoader().parseClass('''
import grails.gorm.hibernate.annotation.ManagedEntity
import grails.gorm.annotation.Entity
@ManagedEntity
@Entity
class MyEntity {
    String name
    String lastName
    int age
    boolean active
    long salary

    @grails.gorm.dirty.checking.DirtyCheckedProperty
    String getName() {
        return this.name
    }

    @grails.gorm.dirty.checking.DirtyCheckedProperty
    void setName(String name) {
        this.name = name
    }

    @grails.gorm.dirty.checking.DirtyCheckedProperty
    String getLastName() {
        return this.lastName
    }

    @grails.gorm.dirty.checking.DirtyCheckedProperty
    void setLastName(String name) {
        this.lastName = name
    }

    @grails.gorm.dirty.checking.DirtyCheckedProperty
    int getAge() {
        return this.age
    }

    @grails.gorm.dirty.checking.DirtyCheckedProperty
    void setAge(int age) {
        this.age = age
    }

    @grails.gorm.dirty.checking.DirtyCheckedProperty
    boolean getActive() {
        return this.active
    }

    @grails.gorm.dirty.checking.DirtyCheckedProperty
    void setActive(boolean active) {
        this.active = active
    }

    @grails.gorm.dirty.checking.DirtyCheckedProperty
    long getSalary() {
        return this.salary
    }

    @grails.gorm.dirty.checking.DirtyCheckedProperty
    void setSalary(long salary) {
        this.salary = salary
    }
}
''')
        then:
        PersistentAttributeInterceptable.isAssignableFrom(cls)
        ManagedEntity.isAssignableFrom(cls)

        when:
        Object myEntity = cls.newInstance()

        ((PersistentAttributeInterceptable)myEntity).$$_hibernate_setInterceptor(
            new PersistentAttributeInterceptor() {
                @Override
                boolean readBoolean(Object obj, String name, boolean oldValue) {
                    return true
                }

                @Override
                boolean writeBoolean(Object obj, String name, boolean oldValue, boolean newValue) {
                    return true
                }

                @Override
                byte readByte(Object obj, String name, byte oldValue) {
                    return 0
                }

                @Override
                byte writeByte(Object obj, String name, byte oldValue, byte newValue) {
                    return 0
                }

                @Override
                char readChar(Object obj, String name, char oldValue) {
                    return 0
                }

                @Override
                char writeChar(Object obj, String name, char oldValue, char newValue) {
                    return 0
                }

                @Override
                short readShort(Object obj, String name, short oldValue) {
                    return 0
                }

                @Override
                short writeShort(Object obj, String name, short oldValue, short newValue) {
                    return 0
                }

                @Override
                int readInt(Object obj, String name, int oldValue) {
                    return 10
                }

                @Override
                int writeInt(Object obj, String name, int oldValue, int newValue) {
                    return 10
                }

                @Override
                float readFloat(Object obj, String name, float oldValue) {
                    return 0
                }

                @Override
                float writeFloat(Object obj, String name, float oldValue, float newValue) {
                    return 0
                }

                @Override
                double readDouble(Object obj, String name, double oldValue) {
                    return 0
                }

                @Override
                double writeDouble(Object obj, String name, double oldValue, double newValue) {
                    return 0
                }

                @Override
                long readLong(Object obj, String name, long oldValue) {
                    return 1000L
                }

                @Override
                long writeLong(Object obj, String name, long oldValue, long newValue) {
                    return 2000L
                }

                @Override
                Object readObject(Object obj, String name, Object oldValue) {
                    return "good"
                }

                @Override
                Object writeObject(Object obj, String name, Object oldValue, Object newValue) {
                    return "changed"
                }

                @Override
                Set<String> getInitializedLazyAttributeNames() {
                    return Collections.emptySet()
                }

                @Override
                void attributeInitialized(String name) {

                }
            }
        )

        then:"the interceptor is used when reading a property"
        myEntity.name == 'good'
        myEntity.lastName == 'good'
        myEntity.age == 10
        myEntity.active == true
        myEntity.salary == 1000L

        when:"A setter is set"
        myEntity.name = 'something'
        myEntity.age = 5
        myEntity.active = false
        myEntity.salary = 500L
        ((PersistentAttributeInterceptable)myEntity).$$_hibernate_setInterceptor( null )

        then:"The value is changed"
        myEntity.name == 'changed'
        myEntity.salary == 2000L

        and: "by transformation added methods are all marked as Generated"
        cls.getMethod('$$_hibernate_getInterceptor').isAnnotationPresent(Generated)
        cls.getMethod('$$_hibernate_setInterceptor', PersistentAttributeInterceptor).isAnnotationPresent(Generated)
        cls.getMethod('$$_hibernate_getEntityInstance').isAnnotationPresent(Generated)
        cls.getMethod('$$_hibernate_getEntityEntry').isAnnotationPresent(Generated)
        cls.getMethod('$$_hibernate_setEntityEntry', EntityEntry).isAnnotationPresent(Generated)
        cls.getMethod('$$_hibernate_getPreviousManagedEntity').isAnnotationPresent(Generated)
        cls.getMethod('$$_hibernate_getNextManagedEntity').isAnnotationPresent(Generated)
        cls.getMethod('$$_hibernate_setPreviousManagedEntity', ManagedEntity).isAnnotationPresent(Generated)
        cls.getMethod('$$_hibernate_setNextManagedEntity', ManagedEntity).isAnnotationPresent(Generated)
        cls.getMethod('$$_hibernate_getInstanceId').isAnnotationPresent(Generated)
        cls.getMethod('$$_hibernate_setInstanceId', int).isAnnotationPresent(Generated)
        cls.getMethod('$$_hibernate_useTracker').isAnnotationPresent(Generated)
        cls.getMethod('$$_hibernate_setUseTracker', boolean).isAnnotationPresent(Generated)
    }

    void "test skip non-hibernate mapping strategy"() {
        when:
        Class cls = new GroovyClassLoader().parseClass('''
import grails.gorm.hibernate.annotation.ManagedEntity
@ManagedEntity
class NonHibernateEntity {
    static mapWith = "mongodb"
}
''')
        then:
        !PersistentAttributeInterceptable.isAssignableFrom(cls)
    }

    void "test addTo retargeting"() {
        when:
        Class cls = new GroovyClassLoader().parseClass('''
import grails.gorm.hibernate.annotation.ManagedEntity
@ManagedEntity
class AddToEntity {
    static hasMany = [items: String]
}
''')
        then:
        PersistentAttributeInterceptable.isAssignableFrom(cls)
        // addToItems is generated by GormEntityTransformation (invoked via visit)
        cls.getDeclaredMethod("addToItems", Object) != null
    }

    void "test inner class and enum skipping"() {
        when:
        Class cls = new GroovyClassLoader().parseClass('''
import grails.gorm.hibernate.annotation.ManagedEntity
class Outer {
    @ManagedEntity
    class Inner {}
    
    @ManagedEntity
    enum MyEnum { VALUE }
}
''')
        then:
        !PersistentAttributeInterceptable.isAssignableFrom(cls.getDeclaredClasses().find { it.simpleName == 'Inner' })
        !PersistentAttributeInterceptable.isAssignableFrom(cls.getDeclaredClasses().find { it.simpleName == 'MyEnum' })
    }

    void "test visit with wrong types"() {
        given:
        def transformation = new HibernateEntityTransformation()
        
        when:
        transformation.visit([new org.codehaus.groovy.ast.expr.ConstantExpression("foo")] as org.codehaus.groovy.ast.ASTNode[], null)
        
        then:
        thrown(RuntimeException)
    }
}
