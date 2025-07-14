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
package org.grails.databinding.converters.web

import groovy.transform.CompileStatic

import java.text.DecimalFormat
import java.text.NumberFormat

/**
 * A ValueConverter that knows how to convert a String to a BigDecimal or a BigInteger and is Locale aware.  The
 * converter will use the Locale of the current request if being invoked as part of a
 * request, otherwise will use Locale.getDefault().
 *
 * @author Jeff Brown
 * @since 2.3
 */
@CompileStatic
class LocaleAwareBigDecimalConverter extends LocaleAwareNumberConverter {

    @Override
    protected NumberFormat getNumberFormatter() {
        def nf = super.getNumberFormatter()
        if (!(nf instanceof DecimalFormat)) {
            throw new IllegalStateException("Cannot support non-DecimalFormat: " + nf)
        }

        ((DecimalFormat) nf).setParseBigDecimal(true)
        nf
    }
}
