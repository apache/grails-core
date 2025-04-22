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

/**
 * Wraps a char array and implements CharSequence interface
 * 
 * 
 * @author Lari Hotari
 * @since 2.3.10
 *
 */
class CharArrayCharSequence implements CharSequence, CharArrayAccessible {
    private final char[] chars;
    private final int count;
    private final int start;

    CharArrayCharSequence(char[] chars, int start, int count) {
        if (start + count > chars.length)
            throw new StringIndexOutOfBoundsException(start);
        this.chars = chars;
        this.start = start;
        this.count = count;
    }

    public char charAt(int index) {
        if ((index < 0) || (index + start >= chars.length))
            throw new StringIndexOutOfBoundsException(index);
        return chars[index + start];
    }

    public int length() {
        return count;
    }

    public CharSequence subSequence(int start, int end) {
        if (start < 0)
            throw new StringIndexOutOfBoundsException(start);
        if (end > count)
            throw new StringIndexOutOfBoundsException(end);
        if (start > end)
            throw new StringIndexOutOfBoundsException(end - start);
        if (start == 0 && end == count) {
            return this;
        }
        return new CharArrayCharSequence(chars, this.start + start, end - start);
    }

    @Override
    public String toString() {
        return new String(chars, start, count);
    }

    public void getChars(int srcBegin, int srcEnd, char[] dst, int dstBegin) {
        if (srcBegin < 0)
            throw new StringIndexOutOfBoundsException(srcBegin);
        if ((srcEnd < 0) || (srcEnd > start+count))
            throw new StringIndexOutOfBoundsException(srcEnd);
        if (srcBegin > srcEnd)
            throw new StringIndexOutOfBoundsException("srcBegin > srcEnd");
        System.arraycopy(chars, start + srcBegin, dst, dstBegin, srcEnd - srcBegin);
    }
}