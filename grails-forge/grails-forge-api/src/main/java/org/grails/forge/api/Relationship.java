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
package org.grails.forge.api;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.naming.Named;

import java.util.Locale;

/**
 * Relationship types.
 *
 * @author graemerocher
 * @since 6.0.0
 */
public enum Relationship implements Named, CharSequence {
    SELF,
    CREATE,
    PREVIEW,
    DIFF;

    @NonNull
    @Override
    public String getName() {
        return name().toLowerCase(Locale.ENGLISH);
    }

    @Override
    public int length() {
        return getName().length();
    }

    @Override
    public char charAt(int index) {
        return getName().charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return getName().subSequence(start, end);
    }

    @Override
    public String toString() {
        return getName();
    }
}
