/*
 * Copyright 2009 the original author or authors.
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
package org.grails.buffer;

import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Field;

/**
 * Provides optimized access to java.lang.String internals
 *
 * - Optimized way of creating java.lang.String by reusing a char[] buffer
 * - Optimized way of writing String to java.io.Writer
 *
 * java.lang.String creation reusing a char[] buffer requires Java 1.5+
 *
 * System property "stringchararrayaccessor.disabled" disables this hack.
 * -Dstringchararrayaccessor.disabled=true
 *
 * Read JSR-133, "9.1.1 Post-Construction Modification of Final Fields"
 * http://www.cs.umd.edu/~pugh/java/memoryModel/jsr133.pdf
 *
 * @author Lari Hotari, Sagire Software Oy
 *
 */
public class StringCharArrayAccessor {
    static volatile boolean enabled = Boolean.getBoolean("stringchararrayaccessor.disabled");
    static volatile boolean jdk7_string = false;

    static Field valueField;
    static Field countField;
    static Field offsetField;

    static {
        if (enabled) {
            try {
                valueField = String.class.getDeclaredField("value");
                valueField.setAccessible(true);
            }
            catch (Exception e) {
                enabled = false;
                handleError(e);
            }
        }
        if (enabled) {
            try {
                countField = String.class.getDeclaredField("count");
                countField.setAccessible(true);

                offsetField = String.class.getDeclaredField("offset");
                offsetField.setAccessible(true);
            }
            catch (NoSuchFieldException e) {
                jdk7_string = true;
            }
            catch (Exception e) {
                enabled = false;
                handleError(e);
            }
        }
    }

    private StringCharArrayAccessor() {
    }

    /**
     * Writes a portion of a string to a target java.io.Writer with direct access to the char[] of the java.lang.String
     *
     * @param  writer
     *            target java.io.Writer for output
     *
     * @param  str
     *         A String
     *
     * @throws  IOException
     *          If an I/O error occurs
     */
    static public void writeStringAsCharArray(Writer writer, String str) throws IOException {
        writeStringAsCharArray(writer, str, 0, str.length());
    }

    /**
     * Writes a portion of a string to a target java.io.Writer with direct access to the char[] of the java.lang.String
     *
     * @param  writer
     *            target java.io.Writer for output
     *
     * @param  str
     *         A String
     *
     * @param  off
     *         Offset from which to start writing characters
     *
     * @param  len
     *         Number of characters to write
     *
     * @throws  IOException
     *          If an I/O error occurs
     */
    static public void writeStringAsCharArray(Writer writer, String str, int off, int len) throws IOException {
        if (!enabled) {
            writeStringFallback(writer, str, off, len);
            return;
        }

        char[] value;
        int internalOffset=0;
        try {
            value = (char[])valueField.get(str);
            if(!jdk7_string) {
                internalOffset = offsetField.getInt(str);
            }
        }
        catch (Exception e) {
            handleError(e);
            writeStringFallback(writer, str, off, len);
            return;
        }
        writer.write(value, internalOffset + off, len);
    }

    private static void writeStringFallback(Writer writer, String str, int off, int len) throws IOException {
        writer.write(str, off, len);
    }

    static char[] getValue(String str) {
        if (!enabled) {
            return getValueFallback(str);
        }

        char[] value = null;
        int internalOffset = 0;
        try {
            value = (char[])valueField.get(str);
            if(!jdk7_string) {
                internalOffset = offsetField.getInt(str);
            }
        }
        catch (Exception e) {
            handleError(e);
        }
        if (value != null && internalOffset==0) {
            return value;
        }

        return getValueFallback(str);
    }

    static char[] getValueFallback(String str) {
        return str.toCharArray();
    }

    /**
     * creates a new java.lang.String by setting the char array directly to the String instance with reflection.
     *
     * @param charBuf
     *        char array to be used as java.lang.String content, don't modify it after passing it.
     * @return new java.lang.String
     */
    public static String createString(char[] charBuf) {
        if (!enabled) {
            return createStringFallback(charBuf);
        }

        String str = new String();
        try {
            // try to prevent possible final field setting execution reordering in JIT (JSR-133/JMM, "9.1.1 Post-Construction Modification of Final Fields")
            // it was a bit unclear for me if this could ever happen in a single thread
            synchronized(str) {
                valueField.set(str, charBuf);
                if(!jdk7_string) {
                    countField.set(str, charBuf.length);
                }
            }
            synchronized(str) {
                // safety check, just to be sure that setting the final fields went ok
                if (str.length() != charBuf.length) {
                    throw new IllegalStateException("Fast java.lang.String construction failed.");
                }
            }
        }
        catch (Exception e) {
            handleError(e);
            str = createStringFallback(charBuf);
        }
        return str;
    }

    private static String createStringFallback(char[] charBuf) {
        return new String(charBuf);
    }

    private static synchronized void handleError(Exception e) {
        enabled = false;
        valueField = null;
        countField = null;
        offsetField = null;
    }

    static public boolean isEnabled() {
        return enabled;
    }
}
