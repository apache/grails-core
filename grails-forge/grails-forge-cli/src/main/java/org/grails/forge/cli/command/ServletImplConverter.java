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
package org.grails.forge.cli.command;

import io.micronaut.core.annotation.Introspected;
import org.grails.forge.options.ServletImpl;
import picocli.CommandLine;

@Introspected
public class ServletImplConverter implements CommandLine.ITypeConverter<ServletImpl> {

    @Override
    public ServletImpl convert(String value) throws Exception {
        if (value == null) {
            return ServletImpl.DEFAULT_OPTION;
        } else {
            for (ServletImpl impl : ServletImpl.values()) {
                if (value.equalsIgnoreCase(impl.toString())) {
                    return impl;
                }
            }
        }
        throw new CommandLine.TypeConversionException("Invalid Servlet implementation selection: " + value);
    }
}
