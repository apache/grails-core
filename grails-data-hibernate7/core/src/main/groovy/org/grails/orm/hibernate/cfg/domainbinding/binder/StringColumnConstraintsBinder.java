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
package org.grails.orm.hibernate.cfg.domainbinding.binder;

import java.util.Objects;
import java.util.Optional;

import org.hibernate.mapping.Column;

import org.grails.datastore.mapping.config.Property;

public class StringColumnConstraintsBinder {

    public void bindStringColumnConstraints(Column column, Property mappedForm) {
        Integer number = Optional.ofNullable(mappedForm.getMaxSize())
                .map(Number::intValue)
                .orElse(getMax(mappedForm).orElse(0));
        if (number > 0) {
            column.setLength(number);
        }
    }

    private Optional<Integer> getMax(Property mappedForm) {
        return Optional.ofNullable(mappedForm.getInList()).flatMap(list -> list.stream()
                .map(this::parseInt)
                .filter(Objects::nonNull)
                .reduce(Integer::max));
    }

    private Integer parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
