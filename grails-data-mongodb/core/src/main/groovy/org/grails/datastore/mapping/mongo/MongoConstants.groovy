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

package org.grails.datastore.mapping.mongo

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic

/**
 * Constants for use with GORM for MongoDB
 *
 * @since 6.0
 * @author Graeme Rocher
 */
@CompileStatic
class MongoConstants {

    public static final String SET_OPERATOR = '$set';
    public static final String UNSET_OPERATOR = '$unset';
    public static final String CODEC_ENGINE = "codec";
    public static final String MONGO_ID_FIELD = "_id";
    public static final String MONGO_CLASS_FIELD = "_class";
    public static final String INC_OPERATOR = '$inc'
    public static final String ASSIGNED_IDENTIFIER_MAPPING = "assigned"


    @CompileDynamic
    public static <T> T mapToObject(Class<T> targetType, Map<String, Object> values) {
        T t = targetType.getDeclaredConstructor().newInstance()
        for (String name in values.keySet()) {
            if (t.respondsTo(name)) {
                t."$name"(values.get(name))
            }
        }
        return t
    }
}
