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
package org.grails.orm.hibernate.cfg.domainbinding

import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import org.hibernate.type.descriptor.WrapperOptions
import org.hibernate.usertype.UserType
import grails.persistence.Entity

/**
 * Domain classes of the former {@code EnumTypeBinderSpec}, a unit spec of the classic domain binder of Grails 8: they stay as fixtures of the
 * differential specs, which compare native binding with the recorded classic schema of every scanned entity.
 */
// --- Supporting Classes ---

enum Status01 { AVAILABLE, OUT_OF_STOCK }

@Entity class Person01 {
    Long id; Status01 status
    static constraints = { status nullable: false }
}
@Entity class Person02 {
    Long id; Status01 status
    static mapping = { status enumType: "string" }
}
@Entity class Person03 {
    Long id; Status01 status
    static mapping = { status enumType: "ordinal" }
}
@Entity class Person04 {
    Long id; Status01 status
    static mapping = { status enumType: "identity", nullable: false }
}
@Entity class Person05 {
    Long id; Status01 status
    static mapping = { status type: UserTypeEnumType, nullable: false }
}
@Entity class PersonWithCollection {
    Long id
    Set<Status01> statuses
}
@Entity class PersonWithExplicitColumn {
    Long id; Status01 status
    static mapping = { status column: "status_col", index: "idx_status" }
}
@Entity class PersonWithColumnExtras {
    Long id; Status01 status
    static mapping = {
        status comment: "the status", defaultValue: "'AVAILABLE'", read: "lower(status)", write: "upper(?)"
    }
}
@Entity class Clown01 extends Person01 { String clownName }

class UserTypeEnumType implements UserType {
    @Override int getSqlType() { 0 }
    @Override Class returnedClass() { Status01 }
    @Override boolean equals(Object x, Object y) { x == y }
    @Override int hashCode(Object x) { x.hashCode() }
    @Override Object nullSafeGet(ResultSet rs, int position, WrapperOptions options) throws SQLException { null }
    @Override void nullSafeSet(PreparedStatement st, Object value, int index, WrapperOptions options) throws SQLException {}
    @Override Object deepCopy(Object value) { value }
    @Override boolean isMutable() { false }
    @Override Serializable disassemble(Object value) { (Serializable)value }
    @Override Object assemble(Serializable cached, Object owner) { cached }
}
