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
package org.grails.forge.options;

import java.util.Optional;

import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;
import jakarta.inject.Singleton;

/**
 * Converts user-supplied selection values (including the legacy {@code hibernate}
 * value) to {@link GormImpl} for HTTP parameter binding.
 */
@Singleton
public class GormImplTypeConverter implements TypeConverter<CharSequence, GormImpl> {

    @Override
    public Optional<GormImpl> convert(CharSequence object, Class<GormImpl> targetType, ConversionContext context) {
        if (object == null) {
            return Optional.empty();
        }
        GormImpl gormImpl = GormImpl.parse(object.toString());
        if (gormImpl == null) {
            context.reject(object,
                    new IllegalArgumentException("Invalid Grails Data implementation selection: " + object));
            return Optional.empty();
        }
        return Optional.of(gormImpl);
    }
}
