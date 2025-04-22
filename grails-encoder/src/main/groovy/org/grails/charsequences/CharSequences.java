/*
 * Copyright 2014 the original author or authors.
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
package org.grails.charsequences;

import java.io.IOException;
import java.io.Writer;

/**
 * Utility functions for handling java.lang.CharSequence instances
 * 
 * 
 * @author Lari Hotari
 * @since 2.3.10
 *
 */
public class CharSequences {
    private CharSequences() {
    }

    public static CharSequence createCharSequence(char[] chars) {
        return new CharArrayCharSequence(chars, 0, chars.length);
    }
    
    public static CharSequence createCharSequence(char[] chars, int start, int count) {
        return new CharArrayCharSequence(chars, start, count);
    }
    
    public static CharSequence createCharSequence(CharSequence str, int start, int count) {
        if(canUseOriginalForSubSequence(str, start, count)) {
            return str;
        } else {
            return new SubCharSequence(str, start, count);
        }
    }
    
    /**
     * Checks if start == 0 and count == length of CharSequence
     * It does this check only for String, StringBuilder and StringBuffer classes which have a fast way to check length
     * 
     * Calculating length on GStringImpl requires building the result which is costly.
     * This helper method is to avoid calling length on other that String, StringBuilder and StringBuffer classes
     * when checking if the input CharSequence instance is already the same as the requested sub sequence
     *
     * @param str CharSequence input
     * @param start start index
     * @param count length on sub sequence
     * @return true if input is String, StringBuilder or StringBuffer class, start is 0 and count is length of input sequence
     */
    public static boolean canUseOriginalForSubSequence(CharSequence str, int start, int count) {
        if (start != 0) return false;
        final Class<?> csqClass = str.getClass();
        return (csqClass == String.class || csqClass == StringBuilder.class || csqClass == StringBuffer.class) && count == str.length();
    }
    
    public static CharSequence createSingleCharSequence(int c) {
        return new SingleCharCharSequence(c);
    }
    
    public static CharSequence createSingleCharSequence(char ch) {
        return new SingleCharCharSequence(ch);
    }
    
    /**
     * Writes a CharSequence instance in the most optimal way to the target writer
     * 
     * 
     * @param target writer
     * @param csq source CharSequence instance
     * @param start start/offset index
     * @param end end index + 1
     * @throws IOException
     */
    public static void writeCharSequence(Writer target, CharSequence csq, int start, int end) throws IOException {
        final Class<?> csqClass = csq.getClass();
        if (csqClass == String.class) {
            target.write((String)csq, start, end - start);
        }
        else if (csqClass == StringBuffer.class) {
            char[] buf = new char[end - start];
            ((StringBuffer)csq).getChars(start, end, buf, 0);
            target.write(buf);
        }
        else if (csqClass == StringBuilder.class) {
            char[] buf = new char[end - start];
            ((StringBuilder)csq).getChars(start, end, buf, 0);
            target.write(buf);
        }
        else if (csq instanceof CharArrayAccessible) {
            char[] buf = new char[end - start];
            ((CharArrayAccessible)csq).getChars(start, end, buf, 0);
            target.write(buf);
        }
        else {
            String str = csq.subSequence(start, end).toString();
            target.write(str, 0, str.length());
        }
    }
    
    public static void writeCharSequence(Writer target, CharSequence csq) throws IOException {
        writeCharSequence(target, csq, 0, csq.length());
    }
    
    /**
     * Provides an optimized way to copy CharSequence content to target array.
     * Uses getChars method available on String, StringBuilder and StringBuffer classes.
     * 
     * Characters are copied from the source sequence <code>csq</code> into the
     * destination character array <code>dst</code>. The first character to
     * be copied is at index <code>srcBegin</code>; the last character to
     * be copied is at index <code>srcEnd-1</code>. The total number of
     * characters to be copied is <code>srcEnd-srcBegin</code>. The
     * characters are copied into the subarray of <code>dst</code> starting
     * at index <code>dstBegin</code> and ending at index:
     * <p><blockquote><pre>
     * dstbegin + (srcEnd-srcBegin) - 1
     * </pre></blockquote>
     *
     * @param      csq        the source CharSequence instance.
     * @param      srcBegin   start copying at this offset.
     * @param      srcEnd     stop copying at this offset.
     * @param      dst        the array to copy the data into.
     * @param      dstBegin   offset into <code>dst</code>.
     * @throws     NullPointerException if <code>dst</code> is
     *             <code>null</code>.
     * @throws     IndexOutOfBoundsException  if any of the following is true:
     *             <ul>
     *             <li><code>srcBegin</code> is negative
     *             <li><code>dstBegin</code> is negative
     *             <li>the <code>srcBegin</code> argument is greater than
     *             the <code>srcEnd</code> argument.
     *             <li><code>srcEnd</code> is greater than
     *             <code>this.length()</code>.
     *             <li><code>dstBegin+srcEnd-srcBegin</code> is greater than
     *             <code>dst.length</code>
     *             </ul>
     */    
    public static void getChars(CharSequence csq, int srcBegin, int srcEnd, char dst[], int dstBegin) {
        final Class<?> csqClass = csq.getClass();
        if (csqClass == String.class) {
            ((String)csq).getChars(srcBegin, srcEnd, dst, dstBegin);
        }
        else if (csqClass == StringBuffer.class) {
            ((StringBuffer)csq).getChars(srcBegin, srcEnd, dst, dstBegin);
        }
        else if (csqClass == StringBuilder.class) {
            ((StringBuilder)csq).getChars(srcBegin, srcEnd, dst, dstBegin);
        }
        else if (csq instanceof CharArrayAccessible) {
            ((CharArrayAccessible)csq).getChars(srcBegin, srcEnd, dst, dstBegin);
        }
        else {
            String str = csq.subSequence(srcBegin, srcEnd).toString();
            str.getChars(0, str.length(), dst, dstBegin);
        }
    }
}
