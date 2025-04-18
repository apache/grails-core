package org.grails.gorm.graphql.types.scalars.coercing

import graphql.language.IntValue
import graphql.language.StringValue
import graphql.schema.Coercing
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import groovy.transform.CompileStatic

import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat

/**
 * Default {@link Date} coercion
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
class DateCoercion implements Coercing<Date, Date> {

    protected List<String> formats
    protected boolean lenient

    DateCoercion(List<String> dateFormats, boolean lenient) {
        this.formats = dateFormats
        this.lenient = lenient
    }

    protected Optional<Date> convert(Object input) {
        if (input instanceof Date) {
            Optional.of((Date) input)
        }
        else if (input instanceof String)  {
            parseDate((String) input)
        }
        else {
            Optional.empty()
        }
    }

    @Override
    Date serialize(Object input) {
        convert(input).orElseThrow {
            throw new CoercingSerializeException("Could not convert ${input.class.name} to a Date")
        }
    }

    @Override
    Date parseValue(Object input) {
        convert(input).orElseThrow {
            throw new CoercingParseValueException("Could not convert ${input.class.name} to a Date")
        }
    }

    @Override
    Date parseLiteral(Object input) {
        if (input instanceof IntValue) {
            new Date(((IntValue) input).value.longValue())
        }
        else if (input instanceof StringValue) {
            parseDate(((StringValue) input).value).orElse(null)
        }
        else {
            null
        }
    }

    protected Optional<Date> parseDate(String value) {
        Date dateValue
        if (!value || !formats) {
            return null
        }
        Exception firstException
        for (String format: formats) {
            if (dateValue == null) {
                DateFormat formatter = new SimpleDateFormat(format)
                try {
                    formatter.lenient = lenient
                    dateValue = formatter.parse((String)value)
                } catch (ParseException e) {
                    firstException = firstException ?: e
                }
            }
        }
        if (dateValue == null) {
            Optional.empty()
        }
        else {
            Optional.of(dateValue)
        }
    }

}
