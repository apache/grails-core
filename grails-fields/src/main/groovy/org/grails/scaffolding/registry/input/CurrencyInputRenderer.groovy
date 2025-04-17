package org.grails.scaffolding.registry.input

import org.grails.scaffolding.model.property.DomainProperty
import org.grails.scaffolding.registry.DomainInputRenderer
import groovy.transform.CompileStatic

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
