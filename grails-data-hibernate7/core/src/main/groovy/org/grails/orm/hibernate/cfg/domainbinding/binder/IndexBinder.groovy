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
package org.grails.orm.hibernate.cfg.domainbinding.binder

import groovy.transform.CompileStatic
import jakarta.annotation.Nonnull
import org.hibernate.mapping.Column
import org.hibernate.mapping.Table

import org.grails.orm.hibernate.cfg.ColumnConfig

@CompileStatic
class IndexBinder {

    void bindIndex(@Nonnull String columnName, @Nonnull Column column, ColumnConfig cc, @Nonnull Table table) {
        if (cc == null) {
            return
        }
        Object indexObj = cc.index
        if (indexObj == null) {
            return
        }
        String indexDef
        if (indexObj instanceof Boolean) {
            if (!((Boolean) indexObj)) {
                return
            }
            indexDef = "${table.name}_${columnName}_idx".toString()
        }
        else {
            String indexStr = indexObj.toString()
            if ('true'.equalsIgnoreCase(indexStr)) {
                indexDef = "${table.name}_${columnName}_idx".toString()
            }
            else if ('false'.equalsIgnoreCase(indexStr)) {
                return
            }
            else {
                indexDef = indexStr
            }
        }
        String[] indices = indexDef.split(',')
        for (String index : indices) {
            table.getOrCreateIndex(index.trim()).addColumn(column)
        }
    }

}
