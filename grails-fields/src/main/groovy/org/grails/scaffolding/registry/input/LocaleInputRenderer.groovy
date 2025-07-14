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

package org.grails.scaffolding.registry.input

import groovy.transform.CompileStatic
import org.grails.scaffolding.model.property.DomainProperty

/**
 * The default renderer for rendering {@link Locale} properties
 *
 * @author James Kleeh
 */
@CompileStatic
class LocaleInputRenderer implements MapToSelectInputRenderer<Locale> {

    String getOptionValue(Locale locale) {
        locale.country ? "${locale.language}, ${locale.country},  ${locale.displayName}" : "${locale.language}, ${locale.displayName}"
    }

    String getOptionKey(Locale locale) {
        locale.country ? "${locale.language}_${locale.country}" : locale.language
    }

    Map<String, String> getOptions() {
        Locale.availableLocales.collectEntries {
            if (it.country || it.language) {
                [(getOptionKey(it)): getOptionValue(it)]
            } else {
                [:]
            }
        }
    }

    Locale getDefaultOption() {
        Locale.default
    }

    @Override
    boolean supports(DomainProperty property) {
        property.type in Locale
    }

}
