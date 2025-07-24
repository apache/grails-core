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
 * The default renderer for rendering {@link Currency} properties
 *
 * @author James Kleeh
 */
@CompileStatic
class CurrencyInputRenderer implements MapToSelectInputRenderer<Currency> {

    String getOptionValue(Currency currency) {
        currency.currencyCode
    }

    String getOptionKey(Currency currency) {
        currency.currencyCode
    }

    protected List<String> getDefaultCurrencyCodes() {
        ['EUR', 'XCD', 'USD', 'XOF', 'NOK', 'AUD',
         'XAF', 'NZD', 'MAD', 'DKK', 'GBP', 'CHF',
         'XPF', 'ILS', 'ROL', 'TRL']
    }

    Map<String, String> getOptions() {
        defaultCurrencyCodes.collectEntries {
            Currency currency = Currency.getInstance(it)
            [(getOptionKey(currency)): getOptionValue(currency)]
        }
    }

    Currency getDefaultOption() {
        Currency.getInstance(Locale.default)
    }

    @Override
    boolean supports(DomainProperty property) {
        property.type in Currency
    }
}
