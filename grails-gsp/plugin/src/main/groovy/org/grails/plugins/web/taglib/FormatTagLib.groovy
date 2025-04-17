/*
 * Copyright 2004-2005 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.plugins.web.taglib

import grails.artefact.TagLibrary
import grails.gsp.TagLib
import groovy.transform.CompileStatic
import org.grails.plugins.web.GrailsTagDateHelper
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import org.grails.encoder.CodecLookup
import org.grails.encoder.Encoder
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.springframework.context.MessageSource
import org.springframework.context.NoSuchMessageException
import org.springframework.util.StringUtils

/**
 * The base application tag library for Grails many of which take inspiration from Rails helpers (thanks guys! :)
 * This tag library tends to get extended by others as tags within here can be re-used in said libraries.
 *
 * @author Jason Rudolph
 * @author Lari Hotari
 * @author Graeme Rocher
 *
 * @since 0.6
 */
@TagLib
class FormatTagLib implements TagLibrary {

    static returnObjectForTags = ['formatBoolean','formatDate','formatNumber','encodeAs']

    MessageSource messageSource
    CodecLookup codecLookup
    GrailsTagDateHelper grailsTagDateHelper

    @CompileStatic
    String messageHelper(String code, Object defaultMessage = null, List args = null, Locale locale = null) {
        if (locale == null) {
            locale = GrailsWebRequest.lookup().getLocale()
        }
        def message
        try {
            message = messageSource.getMessage(code, args == null ? null : args.toArray(), locale)
        }
        catch (NoSuchMessageException e) {
            if (defaultMessage != null) {
                if (defaultMessage instanceof Closure) {
                    message = defaultMessage()
                }
                else {
                    message = defaultMessage as String
                }
            }
        }
        return message
    }

    /**
     * Outputs the given boolean as the specified text label. If the
     * <code>true</code> and <code>false</code> option are not given,
     * then the boolean is output using the default label.<br/>
     *
     * Examples:<br/>
     *
     * &lt;g:formatBoolean boolean="${myBoolean}" /&gt;<br/>
     * &lt;g:formatBoolean boolean="${myBoolean}" true="True!" false="False!" /&gt;<br/>
     *
     * @emptyTag
     *
     * @attr boolean REQUIRED the boolean to output
     * @attr true text label for boolean true value
     * @attr false text label for boolean false value
     * @attr locale Force the locale for formatting.
     */
    Closure formatBoolean = { attrs ->
        if (!attrs.containsKey("boolean")) {
            throwTagError("Tag [formatBoolean] is missing required attribute [boolean]")
        }

        def b = attrs['boolean']
        if (b == null) {
            return
        }

        if (!(b instanceof Boolean)) {
            b = Boolean.valueOf(b)
        }

        def locale = resolveLocale(attrs.locale)
        if (b) {
            if (attrs.containsKey('true')) {
                return attrs['true']
            }
            return messageHelper('boolean.true', { messageHelper('default.boolean.true', 'True', null, locale) }, null, locale)
        }

        if (attrs.containsKey('false')) {
            return attrs['false']
        }
        return messageHelper('boolean.false', { messageHelper('default.boolean.false', 'False', null, locale) }, null, locale)
    }

    /**
     * Outputs the given <code>Date</code> object in the specified format. If
     * the <code>date</code> is not given, then the current date/time is used.
     * If the <code>format</code> option is not given, then the date is output
     * using the default format.<br/>
     *
     * e.g., &lt;g:formatDate date="${myDate}" format="yyyy-MM-dd HH:mm" /&gt;<br/>
     *
     * @see java.text.SimpleDateFormat
     *
     * @emptyTag
     *
     * @attr date the date object to display; defaults to now if not specified
     * @attr format The formatting pattern to use for the date, see SimpleDateFormat
     * @attr formatName Look up format from the default MessageSource / ResourceBundle (i18n/*.properties file) with this key. If format and formatName are empty, format is looked up with 'default.date.format' key. If the key is missing, 'yyyy-MM-dd HH:mm:ss z' formatting pattern is used.
     * @attr type The type of format to use for the date / time. format or formatName aren't used when type is specified. Possible values: 'date' - shows only date part, 'time' - shows only time part, 'both'/'datetime' - shows date and time
     * @attr timeZone the time zone for formatting. See TimeZone class.
     * @attr locale Force the locale for formatting.
     * @attr style Use default date/time formatting of the country specified by the locale. Possible values: SHORT (default), MEDIUM, LONG, FULL . See DateFormat for explanation.
     * @attr dateStyle Set separate style for the date part.
     * @attr timeStyle Set separate style for the time part.
     */
    Closure formatDate = { attrs ->

        def date
        if (attrs.containsKey('date')) {
            date = attrs.date
            if (date == null) return
        }
        else {
            date = new Date()
        }

        def locale = resolveLocale(attrs.locale)
        String timeStyle = null
        String dateStyle = null
        if (attrs.style != null) {
            String style = attrs.style.toString().toUpperCase()
            timeStyle = style
            dateStyle = style
        }

        if (attrs.dateStyle != null) {
            dateStyle = attrs.dateStyle.toString().toUpperCase()
        }
        if (attrs.timeStyle != null) {
            timeStyle = attrs.timeStyle.toString().toUpperCase()
        }
        String type = attrs.type?.toString()?.toUpperCase()
        def formatName = attrs.formatName
        String format = attrs.format
        def timeZone = grailsTagDateHelper.getTimeZone(attrs.timeZone)

        def dateFormat
        if (!type) {
            if (!format && formatName) {
                format = messageHelper(formatName,null,null,locale)
                if (!format) {
                    throwTagError("Attribute [formatName] of Tag [formatDate] specifies a format key [$formatName] that does not exist within a message bundle!")
                }
            }
            else if (!format) {
                format = messageHelper('date.format', { messageHelper('default.date.format', 'yyyy-MM-dd HH:mm:ss z', null, locale) }, null, locale)
            }

            dateFormat = grailsTagDateHelper.getFormatFromPattern(format, timeZone, locale)
        }
        else {
            if (type=='DATE') {
                dateFormat = grailsTagDateHelper.getDateFormat(dateStyle, timeZone, locale)
            }
            else if (type=='TIME') {
                dateFormat = grailsTagDateHelper.getTimeFormat(timeStyle, timeZone, locale)
            }
            else { // 'both' or 'datetime'
                dateFormat = grailsTagDateHelper.getDateTimeFormat(dateStyle, timeStyle, timeZone, locale)
            }
        }

        return grailsTagDateHelper.format(dateFormat, date)
    }

    /**
     * Outputs the given number in the specified format.  If the
     * <code>format</code> option is not given, then the number is output
     * using the default format.<br/>
     *
     * e.g., &lt;g:formatNumber number="${myNumber}" format="###,##0" /&gt;
     *
     * @see java.text.DecimalFormat
     *
     * @emptyTag
     *
     * @attr number REQUIRED the number to display
     * @attr format The formatting pattern to use for the number, see DecimalFormat
     * @attr formatName Look up format from the default MessageSource / ResourceBundle (i18n/.properties file) with this key.Look up format from the default MessageSource / ResourceBundle (i18n/.properties file) with this key. If format and formatName are empty, format is looked up with 'default.number.format' key. If the key is missing, '0' formatting pattern is used.
     * @attr type The type of formatter to use: 'number', 'currency' or 'percent' . format or formatName aren't used when type is specified.
     * @attr locale Override the locale of the request , String or java.util.Locale value
     * @attr groupingUsed Set whether or not grouping will be used in this format.
     * @attr minIntegerDigits Sets the minimum number of digits allowed in the integer portion of a number.
     * @attr maxIntegerDigits Sets the maximum number of digits allowed in the integer portion of a number.
     * @attr minFractionDigits Sets the minimum number of digits allowed in the fraction portion of a number.
     * @attr maxFractionDigits Sets the maximum number of digits allowed in the fraction portion of a number.
     * @attr currencyCode The standard currency code ('EUR', 'USD', etc.), uses formatting settings for the currency. type='currency' attribute is recommended.
     * @attr currencySymbol Force the currency symbol to some symbol, recommended way is to use currencyCode attribute instead (takes symbol information from java.util.Currency)
     * @attr roundingMode Sets the RoundingMode used in this DecimalFormat. Usual values: HALF_UP, HALF_DOWN. If roundingMode is UNNECESSARY and ArithemeticException raises, the original number formatted with default number formatting will be returned.
     * @attr nan String to be used for display if numberic value is NaN
     */
    Closure formatNumber = { attrs ->
        if (!attrs.containsKey('number')) {
            throwTagError("Tag [formatNumber] is missing required attribute [number]")
        }

        def number = attrs.number
        if (number == null) return

        def formatName = attrs.formatName
        def format = attrs.format
        def type = attrs.type
        def locale = resolveLocale(attrs.locale)

        if (type == null) {
            if (!format && formatName) {
                format = messageHelper(formatName,null,null,locale)
                if (!format) {
                    throwTagError("Attribute [formatName] of Tag [formatNumber] specifies a format key [$formatName] that does not exist within a message bundle!")
                }
            }
            else if (!format) {
                format = messageHelper("number.format", { messageHelper("default.number.format", "0", null, locale) } ,null ,locale)
            }
        }

        DecimalFormatSymbols dcfs = locale ? new DecimalFormatSymbols(locale) : new DecimalFormatSymbols()

        DecimalFormat decimalFormat
        if (!type) {
            decimalFormat = new DecimalFormat(format, dcfs)
        }
        else {
            if (type == 'currency') {
                decimalFormat = NumberFormat.getCurrencyInstance(locale)
            }
            else if (type == 'number') {
                decimalFormat = NumberFormat.getNumberInstance(locale)
            }
            else if (type == 'percent') {
                decimalFormat = NumberFormat.getPercentInstance(locale)
            }
            else {
                throwTagError("Attribute [type] of Tag [formatNumber] specifies an unknown type. Known types are currency, number and percent.")
            }
        }

        if (attrs.nan) {
            dcfs.naN = attrs.nan
            decimalFormat.decimalFormatSymbols = dcfs
        }

        // ensure formatting accuracy
        decimalFormat.setParseBigDecimal(true)

        if (attrs.currencyCode != null) {
            Currency currency = Currency.getInstance(attrs.currencyCode as String)
            decimalFormat.setCurrency(currency)
        }
        if (attrs.currencySymbol != null) {
            dcfs = decimalFormat.getDecimalFormatSymbols()
            dcfs.setCurrencySymbol(attrs.currencySymbol as String)
            decimalFormat.setDecimalFormatSymbols(dcfs)
        }
        if (attrs.groupingUsed != null) {
            if (attrs.groupingUsed instanceof Boolean) {
                decimalFormat.setGroupingUsed(attrs.groupingUsed)
            }
            else {
                // accept true, y, 1, yes
                decimalFormat.setGroupingUsed(attrs.groupingUsed.toString().toBoolean() ||
                    attrs.groupingUsed.toString() == 'yes')
            }
        }
        if (attrs.maxIntegerDigits != null) {
            decimalFormat.setMaximumIntegerDigits(attrs.maxIntegerDigits as Integer)
        }
        if (attrs.minIntegerDigits != null) {
            decimalFormat.setMinimumIntegerDigits(attrs.minIntegerDigits as Integer)
        }
        if (attrs.maxFractionDigits != null) {
            decimalFormat.setMaximumFractionDigits(attrs.maxFractionDigits as Integer)
        }
        if (attrs.minFractionDigits != null) {
            decimalFormat.setMinimumFractionDigits(attrs.minFractionDigits as Integer)
        }
        if (attrs.roundingMode != null) {
            def roundingMode = attrs.roundingMode
            if (!(roundingMode instanceof RoundingMode)) {
                roundingMode = RoundingMode.valueOf(roundingMode)
            }
            decimalFormat.setRoundingMode(roundingMode)
        }

        if (!(number instanceof Number)) {
            number = decimalFormat.parse(number as String)
        }

        def formatted
        try {
            formatted = decimalFormat.format(number)
        }
        catch(ArithmeticException e) {
            // if roundingMode is UNNECESSARY and ArithemeticException raises, just return original number formatted with default number formatting
            formatted = NumberFormat.getNumberInstance(locale).format(number)
        }
        return formatted
    }

    @CompileStatic
    static Locale resolveLocale(Object localeAttr) {
        Locale locale
        if (localeAttr instanceof Locale) {
            locale = (Locale)localeAttr
        } else if (localeAttr != null) {
            locale = StringUtils.parseLocaleString(localeAttr.toString())
        }
        if (locale == null) {
            locale = GrailsWebRequest.lookup().getLocale()
            if (locale == null) {
                locale = Locale.getDefault()
            }
        }
        return locale
    }

    /**
     * Encodes the body using the specified codec.
     *
     * @attr codec REQUIRED the codec name
     */
    Closure encodeAs = { attrs, body ->
        if (!attrs.codec) {
            throwTagError("Tag [encodeAs] requires a codec name in the [codec] attribute")
        }
        Encoder encoder = codecLookup.lookupEncoder(attrs.codec.toString())
        return encoder.encode(body())
    }
}
