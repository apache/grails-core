/*
 * Copyright 2013 the original author or authors.
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
 * Marker interface for telling that the underlying char array is directly accessible
 *
 * This interface is missing from the JVM although String, StringBuffer and StringBuilder all have this method.
 *
 * @author Lari Hotari
 * @since 2.3
 */
public interface CharArrayAccessible {

    /**
     * Characters are copied from this sequence into the
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
    void getChars(int srcBegin, int srcEnd, char dst[], int dstBegin);
}
