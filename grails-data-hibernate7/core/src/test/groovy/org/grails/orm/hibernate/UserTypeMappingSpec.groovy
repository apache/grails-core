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
package org.grails.orm.hibernate

import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Types

import groovy.transform.CompileStatic

import grails.gorm.annotation.Entity
import grails.gorm.hibernate.HibernateEntity
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.orm.hibernate.cfg.Settings
import org.hibernate.mapping.SimpleValue
import org.hibernate.type.descriptor.WrapperOptions
import org.hibernate.usertype.UserType
import spock.lang.Specification
import spock.lang.Unroll

class UserTypeMappingSpec extends Specification {

    @Unroll
    void "a user-type in the default mapping given as #description applies to every entity"() {
        given: 'a datastore whose default mapping registers a user type for Boolean'
        HibernateDatastore datastore = createDatastore("userTypeDefault${iteration}", {
            'user-type'(type: userType, 'class': Boolean)
        }, UserTypeFlag, OtherUserTypeFlag)

        when: 'entities with a Boolean property are saved and read back'
        Map<String, Boolean> readBack = saveAndReadBack(UserTypeFlag)
        Map<String, Boolean> otherReadBack = saveAndReadBack(OtherUserTypeFlag)

        then: 'each property is bound to the user type'
        typeNameOf(datastore, UserTypeFlag) == YesNoBooleanUserType.name
        typeNameOf(datastore, OtherUserTypeFlag) == YesNoBooleanUserType.name

        and: 'the user type writes each column'
        storedFlags(datastore, 'user_type_flag') == [disabled: 'N', enabled: 'Y']
        storedFlags(datastore, 'other_user_type_flag') == [disabled: 'N', enabled: 'Y']

        and: 'the user type reads each column'
        readBack == [disabled: false, enabled: true]
        otherReadBack == [disabled: false, enabled: true]

        cleanup:
        datastore?.close()

        where:
        description     | iteration | userType
        'a Class'       | 1         | YesNoBooleanUserType
        'a class name'  | 2         | YesNoBooleanUserType.name
    }

    void "a user-type in an entity's own mapping applies only to that entity"() {
        given: 'a datastore without a default user type'
        HibernateDatastore datastore = createDatastore('userTypeOwn', {}, OwnUserTypeFlag, UserTypeFlag)

        when: 'the entity that registers the user type and another entity are saved and read back'
        Map<String, Boolean> readBack = saveAndReadBack(OwnUserTypeFlag)
        Map<String, Boolean> otherReadBack = saveAndReadBack(UserTypeFlag)

        then: 'its property is bound to the user type'
        typeNameOf(datastore, OwnUserTypeFlag) == YesNoBooleanUserType.name
        storedFlags(datastore, 'own_user_type_flag') == [disabled: 'N', enabled: 'Y']
        readBack == [disabled: false, enabled: true]

        and: 'other entities keep the standard type'
        typeNameOf(datastore, UserTypeFlag) == Boolean.name
        storedFlags(datastore, 'user_type_flag') == [disabled: 'FALSE', enabled: 'TRUE']
        otherReadBack == [disabled: false, enabled: true]

        cleanup:
        datastore?.close()
    }

    void "a type set on a property takes precedence over a registered user-type"() {
        given: 'a datastore whose default mapping registers a user type for Boolean'
        HibernateDatastore datastore = createDatastore('userTypePrecedence', {
            'user-type'(type: YesNoBooleanUserType, 'class': Boolean)
        }, TypedUserTypeFlag)

        when: 'an entity that sets the type of its Boolean property is saved and read back'
        Map<String, Boolean> readBack = saveAndReadBack(TypedUserTypeFlag)

        then: 'the property keeps the type it sets'
        typeNameOf(datastore, TypedUserTypeFlag) == Boolean.name
        storedFlags(datastore, 'typed_user_type_flag') == [disabled: 'FALSE', enabled: 'TRUE']
        readBack == [disabled: false, enabled: true]

        cleanup:
        datastore?.close()
    }

    private static HibernateDatastore createDatastore(String databaseName, Closure defaultMapping, Class... entities) {
        new HibernateDatastore(DatastoreUtils.createPropertyResolver(
                (Settings.SETTING_DB_CREATE): 'create-drop',
                'dataSource.url': "jdbc:h2:mem:${databaseName};LOCK_TIMEOUT=10000",
                'grails.gorm.default.mapping': defaultMapping
        ), entities)
    }

    private static Map<String, Boolean> saveAndReadBack(Class entity) {
        entity.withTransaction {
            entity.newInstance(name: 'enabled', active: true).save(flush: true, failOnError: true)
            entity.newInstance(name: 'disabled', active: false).save(flush: true, failOnError: true)
        }
        entity.withTransaction {
            entity.list().collectEntries { [(it.name): it.active] }
        } as Map<String, Boolean>
    }

    private static String typeNameOf(HibernateDatastore datastore, Class entity) {
        (datastore.metadata.getEntityBinding(entity.name).getProperty('active').value as SimpleValue).typeName
    }

    private static Map<String, String> storedFlags(HibernateDatastore datastore, String table) {
        Map<String, String> flags = [:]
        datastore.dataSource.connection.withCloseable { conn ->
            conn.createStatement().withCloseable { stmt ->
                stmt.executeQuery("select name, active from ${table} order by name").withCloseable { rs ->
                    while (rs.next()) {
                        flags[rs.getString('name')] = rs.getString('active')
                    }
                }
            }
        }
        flags
    }
}

@Entity
class UserTypeFlag implements HibernateEntity<UserTypeFlag> {
    String name
    Boolean active
}

@Entity
class OtherUserTypeFlag implements HibernateEntity<OtherUserTypeFlag> {
    String name
    Boolean active
}

@Entity
class OwnUserTypeFlag implements HibernateEntity<OwnUserTypeFlag> {
    String name
    Boolean active

    static mapping = {
        'user-type'(type: YesNoBooleanUserType, 'class': Boolean)
    }
}

@Entity
class TypedUserTypeFlag implements HibernateEntity<TypedUserTypeFlag> {
    String name
    Boolean active

    static mapping = {
        active type: Boolean
    }
}

@CompileStatic
class YesNoBooleanUserType implements UserType<Boolean> {

    @Override
    int getSqlType() {
        Types.VARCHAR
    }

    @Override
    Class<Boolean> returnedClass() {
        Boolean
    }

    @Override
    Boolean nullSafeGet(ResultSet rs, int position, WrapperOptions options) throws SQLException {
        String value = rs.getString(position)
        value == null ? null : value == 'Y'
    }

    @Override
    void nullSafeSet(PreparedStatement st, Boolean value, int index, WrapperOptions options) throws SQLException {
        if (value == null) {
            st.setNull(index, Types.VARCHAR)
        } else {
            st.setString(index, value ? 'Y' : 'N')
        }
    }

    @Override
    Boolean deepCopy(Boolean value) {
        value
    }

    @Override
    boolean isMutable() {
        false
    }
}
