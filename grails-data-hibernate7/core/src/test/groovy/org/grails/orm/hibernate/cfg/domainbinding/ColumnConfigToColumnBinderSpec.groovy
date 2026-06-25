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

import org.grails.orm.hibernate.cfg.ColumnConfig
import org.grails.orm.hibernate.cfg.PropertyConfig
import org.hibernate.mapping.Column
import spock.lang.Specification

import org.grails.orm.hibernate.cfg.domainbinding.binder.ColumnConfigToColumnBinder

class ColumnConfigToColumnBinderSpec extends Specification {

    def binder = new ColumnConfigToColumnBinder()
    def column = new Column("test")

    def "should bind column properties when values are valid"() {
        given:
        def columnConfig = new ColumnConfig()
        columnConfig.length = 100
        columnConfig.precision = 10
        columnConfig.scale = 2
        columnConfig.sqlType = "VARCHAR"
        columnConfig.unique = true

        when:
        binder.bindColumnConfigToColumn(column, columnConfig, new PropertyConfig())

        then:
        column.length == 100
        column.precision == 10
        column.scale == 2
        column.sqlType == "VARCHAR"
        column.unique
    }

    def "should not set precision or scale when values are -1"() {
        given:
        def columnConfig = new ColumnConfig()
        columnConfig.length = -1
        columnConfig.precision = -1
        columnConfig.scale = -1

        when:
        binder.bindColumnConfigToColumn(column, columnConfig, null)

        then:
        column.length == null
        column.precision == null
        column.scale == null
        column.sqlType == null
        !column.unique
    }

    def "should not set precision or scale when columnConfig is null"() {
        when:
        binder.bindColumnConfigToColumn(column, null, null)

        then:
        column.precision == null
        column.scale == null
    }

    def "column config honors uniqueness property"() {
        given:
        def columnConfig = new ColumnConfig()
        columnConfig.length = -1
        columnConfig.precision = -1
        columnConfig.scale = -1
        PropertyConfig mappedForm = new PropertyConfig()
        mappedForm.setUnique("name")

        when:
        binder.bindColumnConfigToColumn(column, columnConfig, mappedForm)

        then:
        column.length == null
        column.precision == null
        column.scale == null
        column.sqlType == null
        !column.unique
    }

    def "column config honors uniqueness property when set to a string (named group)"() {
        given:
        def columnConfig = new ColumnConfig(unique: "group1")
        PropertyConfig mappedForm = new PropertyConfig(unique: "group1")

        when:
        binder.bindColumnConfigToColumn(column, columnConfig, mappedForm)

        then:
        !column.unique
    }

    def "column config honors uniqueness property when set to a list (composite groups)"() {
        given:
        def columnConfig = new ColumnConfig(unique: ["group1", "group2"])
        PropertyConfig mappedForm = new PropertyConfig(unique: ["group1", "group2"])

        when:
        binder.bindColumnConfigToColumn(column, columnConfig, mappedForm)

        then:
        !column.unique
    }

    def "column config honors uniqueness property when set to boolean true"() {
        given:
        def columnConfig = new ColumnConfig(unique: true)
        PropertyConfig mappedForm = new PropertyConfig(unique: true)

        when:
        binder.bindColumnConfigToColumn(column, columnConfig, mappedForm)

        then:
        column.unique
    }

    def "column config honors uniqueness property when set to boolean false"() {
        given:
        def columnConfig = new ColumnConfig(unique: false)
        PropertyConfig mappedForm = new PropertyConfig(unique: false)

        when:
        binder.bindColumnConfigToColumn(column, columnConfig, mappedForm)

        then:
        !column.unique
    }

    def "column config honors uniqueness property when mappedForm is empty"() {
        given:
        def columnConfig = new ColumnConfig()
        columnConfig.length = -1
        columnConfig.precision = -1
        columnConfig.scale = -1
        PropertyConfig mappedForm = new PropertyConfig()

        when:
        binder.bindColumnConfigToColumn(column, columnConfig, mappedForm)

        then:
        column.length == null
        column.precision == null
        column.scale == null
        column.sqlType == null
        !column.unique
    }
}
