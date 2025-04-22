/* Copyright (C) 2010 SpringSource
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.datastore.mapping.model.types;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterRegistry;

/**
 * A registrar that registers basic type converters
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class BasicTypeConverterRegistrar {

    public void register(ConverterRegistry registry) {
        registry.addConverter(new Converter<Class, String>() {
            @Override
            public String convert(Class aClass) {
                return aClass.getName();
            }
        });

        registry.addConverter(new Converter<Date, String>() {
            public String convert(Date date) {
                return String.valueOf(date.getTime());
            }
        });

        registry.addConverter(new Converter<Date, Calendar>() {
            public Calendar convert(Date date) {
                final GregorianCalendar calendar = new GregorianCalendar();
                calendar.setTime(date);
                return calendar;
            }
        });

        registry.addConverter(new Converter<Integer, Long>() {
            public Long convert(Integer integer) {
                return integer.longValue();
            }
        });

        registry.addConverter(new Converter<Long, Integer>() {
             public Integer convert(Long longValue) {
                 return longValue.intValue();
             }
         });

        registry.addConverter(new Converter<Integer, Double>() {
            public Double convert(Integer integer) {
                return integer.doubleValue();
            }
        });

        registry.addConverter(new Converter<CharSequence, Date>() {
            public Date convert(CharSequence s) {
                try {
                    final Long time = Long.valueOf(s.toString());
                    return new Date(time);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(e);
                }
            }
        });

        registry.addConverter(new Converter<CharSequence, Double>() {
            public Double convert(CharSequence s) {
                try {
                    return Double.valueOf(s.toString());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(e);
                }
            }
        });

        registry.addConverter(new Converter<CharSequence, Integer>() {
            public Integer convert(CharSequence s) {
                try {
                    return Integer.valueOf(s.toString());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(e);
                }
            }
        });

        registry.addConverter(new Converter<CharSequence, Long>() {
            public Long convert(CharSequence s) {
                try {
                    return Long.valueOf(s.toString());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(e);
                }
            }
        });

        registry.addConverter(new Converter<Object, String>() {
            public String convert(Object o) {
                return o.toString();
            }
        });

        registry.addConverter(new Converter<Calendar, String>() {
            public String convert(Calendar calendar) {
                return String.valueOf(calendar.getTime().getTime());
            }
        });

        registry.addConverter(new Converter<CharSequence, Calendar>() {
            public Calendar convert(CharSequence s) {
                try {
                    Date date = new Date(Long.valueOf(s.toString()));
                    Calendar c = new GregorianCalendar();
                    c.setTime(date);
                    return c;
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(e);
                }
            }
        });
    }
}
