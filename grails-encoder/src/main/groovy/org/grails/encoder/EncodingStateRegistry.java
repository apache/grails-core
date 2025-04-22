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
 * EncodingStateRegistry keeps encoding state of CharSequence instances.
 * Encoding state information is required for the solution that prevents
 * double-encoding a value that is already encoded. In the current
 * implementation, a single EncodingStateRegistry instance is bound to a HTTP
 * request life cycle.
 *
 * @author Lari Hotari
 * @since 2.3
 */
public interface EncodingStateRegistry {

    /**
     * Gets the current encoding state for a CharSequence.
     *
     * @param string
     *            a CharSequence
     * @return the encoding state for the CharSequence
     */
    EncodingState getEncodingStateFor(CharSequence string);

    /**
     * Checks if a encoder should be applied to a CharSequence
     *
     * @param encoderToApply
     *            the encoder to apply
     * @param string
     *            a CharSequence
     * @return true, if it should be applied
     */
    boolean shouldEncodeWith(Encoder encoderToApply, CharSequence string);

    /**
     * Checks if the CharSequence is encoded with encoder.
     *
     * @param encoder
     *            the encoder
     * @param string
     *            a CharSequence
     * @return true, if it is encoded with encoder
     */
    boolean isEncodedWith(Encoder encoder, CharSequence string);

    /**
     * Registers that the CharSequence has been encoded with encoder
     *
     * @param encoder
     *            the encoder
     * @param escaped
     *            the CharSequence
     */
    void registerEncodedWith(Encoder encoder, CharSequence escaped);
}
