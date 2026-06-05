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
package org.grails.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import grails.util.GrailsStringUtils;

/**
 * Internal helpers that convert a raw {@code Object} value to a target type without allocating
 * an intermediate map. These power the type conversion methods on {@code TypeConvertingMap} and
 * the servlet attribute extensions, keeping the conversion logic in a single place.
 *
 * <p>This is an internal class and is not part of the public Grails API.
 *
 * @since 8.0
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public final class TypeConverters {

    public static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss.S";

    private TypeConverters() {
    }

    public static Byte toByte(Object o) {
        if (o instanceof Number) {
            return ((Number) o).byteValue();
        }
        if (o != null) {
            try {
                String string = o.toString();
                if (string != null && string.length() > 0) {
                    return Byte.parseByte(string);
                }
            }
            catch (NumberFormatException ignored) {}
        }
        return null;
    }

    public static Character toCharacter(Object o) {
        if (o instanceof Character) {
            return (Character) o;
        }
        if (o != null) {
            String string = o.toString();
            if (string != null && string.length() == 1) {
                return string.charAt(0);
            }
        }
        return null;
    }

    public static Integer toInteger(Object o) {
        if (o instanceof Number) {
            return ((Number) o).intValue();
        }
        if (o != null) {
            try {
                String string = o.toString();
                if (string != null) {
                    return Integer.parseInt(string);
                }
            }
            catch (NumberFormatException ignored) {}
        }
        return null;
    }

    public static Long toLong(Object o) {
        if (o instanceof Number) {
            return ((Number) o).longValue();
        }
        if (o != null) {
            try {
                return Long.parseLong(o.toString());
            }
            catch (NumberFormatException ignored) {}
        }
        return null;
    }

    public static Short toShort(Object o) {
        if (o instanceof Number) {
            return ((Number) o).shortValue();
        }
        if (o != null) {
            try {
                String string = o.toString();
                if (string != null) {
                    return Short.parseShort(string);
                }
            }
            catch (NumberFormatException ignored) {}
        }
        return null;
    }

    public static Double toDouble(Object o) {
        if (o instanceof Number) {
            return ((Number) o).doubleValue();
        }
        if (o != null) {
            try {
                String string = o.toString();
                if (string != null) {
                    return Double.parseDouble(string);
                }
            }
            catch (NumberFormatException ignored) {}
        }
        return null;
    }

    public static Float toFloat(Object o) {
        if (o instanceof Number) {
            return ((Number) o).floatValue();
        }
        if (o != null) {
            try {
                String string = o.toString();
                if (string != null) {
                    return Float.parseFloat(string);
                }
            }
            catch (NumberFormatException ignored) {}
        }
        return null;
    }

    public static Boolean toBoolean(Object o) {
        if (o instanceof Boolean) {
            return (Boolean) o;
        }
        if (o != null) {
            try {
                String string = o.toString();
                if (string != null) {
                    return GrailsStringUtils.toBoolean(string);
                }
            }
            catch (Exception ignored) {}
        }
        return null;
    }

    public static String toStringValue(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Object[]) {
            Object[] array = (Object[]) o;
            return array.length > 0 && array[0] != null ? array[0].toString() : null;
        }
        return o.toString();
    }

    public static Date toDate(Object value) {
        return toDate(value, DEFAULT_DATE_FORMAT);
    }

    public static Date toDate(Object value, String format) {
        if (value instanceof Date) {
            return (Date) value;
        }
        if (value != null) {
            String string = value.toString();
            if (string != null) {
                try {
                    return new SimpleDateFormat(format).parse(string);
                } catch (ParseException ignored) {}
            }
        }
        return null;
    }

    public static Date toDate(Object value, Collection<String> formats) {
        for (String format : formats) {
            Date date = toDate(value, format);
            if (date != null) return date;
        }
        return null;
    }

    public static List toList(Object paramValues) {
        if (paramValues == null) {
            return Collections.emptyList();
        }
        if (paramValues.getClass().isArray()) {
            return Arrays.asList((Object[]) paramValues);
        }
        if (paramValues instanceof Collection) {
            return new ArrayList((Collection) paramValues);
        }
        return Collections.singletonList(paramValues);
    }

    public static Byte toByte(Object o, Integer defaultValue) {
        Byte value = toByte(o);
        if (value == null && defaultValue != null) {
            return (byte) defaultValue.intValue();
        }
        return value;
    }

    public static Character toCharacter(Object o, Integer defaultValue) {
        Character value = toCharacter(o);
        if (value == null && defaultValue != null) {
            return (char) defaultValue.intValue();
        }
        return value;
    }

    public static Character toCharacter(Object o, Character defaultValue) {
        Character value = toCharacter(o);
        return value != null ? value : defaultValue;
    }

    public static Short toShort(Object o, Integer defaultValue) {
        Short value = toShort(o);
        if (value == null && defaultValue != null) {
            return defaultValue.shortValue();
        }
        return value;
    }

    public static Integer toInteger(Object o, Integer defaultValue) {
        Integer value = toInteger(o);
        return value != null ? value : defaultValue;
    }

    public static Long toLong(Object o, Long defaultValue) {
        Long value = toLong(o);
        return value != null ? value : defaultValue;
    }

    public static Double toDouble(Object o, Double defaultValue) {
        Double value = toDouble(o);
        return value != null ? value : defaultValue;
    }

    public static Float toFloat(Object o, Float defaultValue) {
        Float value = toFloat(o);
        return value != null ? value : defaultValue;
    }

    public static String toStringValue(Object o, String defaultValue) {
        String value = toStringValue(o);
        return value != null ? value : defaultValue;
    }
}
