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
package grails.web;

import grails.util.GrailsNameUtils;
import org.springframework.util.StringUtils;


/**
 * URL converter that allows for camel case URLs
 *
 * @author Jeff Brown
 * @since 2.0
 */
public class CamelCaseUrlConverter implements UrlConverter {

    public String toUrlElement(String propertyOrClassName) {
        if (!StringUtils.hasText(propertyOrClassName)) {
            return propertyOrClassName;
        }

        if (propertyOrClassName.contains(".")) {
            String[] parts = propertyOrClassName.split("\\.");
            StringBuilder buffer = new StringBuilder();
            int last = parts.length - 1;
            for (int i = 0; i < parts.length; i++) {
                buffer.append(GrailsNameUtils.getPropertyName(parts[i]));
                if (i < last) {
                    buffer.append(".");
                }
            }
            return buffer.toString();
        } else {
            return GrailsNameUtils.getPropertyName(propertyOrClassName);
        }
    }
}
