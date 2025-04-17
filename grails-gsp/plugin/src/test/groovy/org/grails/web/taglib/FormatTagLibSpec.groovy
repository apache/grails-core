package org.grails.web.taglib

import grails.testing.web.taglib.TagLibUnitTest
import org.grails.plugins.web.taglib.FormatTagLib
import spock.lang.IgnoreIf
import spock.lang.Requires
import spock.lang.Specification

class FormatTagLibSpec extends Specification implements TagLibUnitTest<FormatTagLib> {

    @Requires({ jvm.isJava8() })
    void testFormatCurrencyForJava8() {
        given:
        BigDecimal number = "3.12325678" as BigDecimal
        "3,12 €" == applyTemplate('<g:formatNumber type="currency" number="${number}" locale="fi_FI" />', [number: number])
    }

    @IgnoreIf({ jvm.isJava8() })
    void testFormatCurrency() {
        given:
        BigDecimal number = "3.12325678" as BigDecimal
        "3,12${new String([160] as char[])}€" == applyTemplate('<g:formatNumber type="currency" number="${number}" locale="fi_FI" />', [number: number])
    }

    @Requires({ jvm.isJava8() })
    void testFormatCurrencyWithCodeAndLocaleForJava8() {
        given:
        BigDecimal number = "3.12325678" as BigDecimal

        expect:
        "3,12 USD" == applyTemplate('<g:formatNumber type="currency" currencyCode="USD" number="${number}" locale="fi_FI" />',  [number: number])
    }

    @IgnoreIf({ jvm.isJava8() })
    void testFormatCurrencyWithCodeAndLocale() {
        given:
        BigDecimal number = "3.12325678" as BigDecimal

        expect:
        "3,12${new String([160] as char[])}\$" == applyTemplate('<g:formatNumber type="currency" currencyCode="USD" number="${number}" locale="fi_FI" />',  [number: number])
    }
}
