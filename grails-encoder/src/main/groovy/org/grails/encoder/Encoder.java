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
package org.grails.encoder;

/**
 * Interface for encoding methods.
 *
 * @author Lari Hotari
 * @since 2.3
 */
public interface Encoder extends CodecIdentifierProvider {

    /**
     * Encode given input object
     *
     * @param o the input object
     * @return the encoded object
     */
    Object encode(Object o);

    /**
     * Checks if this encoder is XSS "safe". This means that after appling this
     * encoder, the characters have been escaped and are XSS safe to be included
     * in HTML documents.
     *
     * @return true, if is safe
     */
    boolean isSafe();

    /**
     * @return <code>true</code> if this this codec should be applied to a buffer part that is
     * already encoded with a safe encoder
     */
    boolean isApplyToSafelyEncoded();

    /**
     * Mark this instance as encoded with this encoder in the current
     * {@link EncodingStateRegistry}
     *
     * @param string a CharSequence to mark as encoded
     */
    void markEncoded(CharSequence string);
}
