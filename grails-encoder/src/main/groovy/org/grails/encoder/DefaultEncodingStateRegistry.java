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

import org.grails.encoder.impl.BasicCodecLookup;
import org.grails.encoder.impl.NoneEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * default implementation of {@link EncodingStateRegistry}
 *
 * @author Lari Hotari
 * @since 2.3
 */
public final class DefaultEncodingStateRegistry implements EncodingStateRegistry {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultEncodingStateRegistry.class);
    private Map<Encoder, Map<Long, WeakReference<CharSequence>>> encodedCharSequencesForEncoder = new HashMap<>();
    public static final StreamingEncoder NONE_ENCODER = BasicCodecLookup.NONE_ENCODER;

    private long calculateKey(CharSequence charSequence) {
        int contentHashCode = charSequence.hashCode();
        int identityHashCode = System.identityHashCode(charSequence);
        // encode both content hash code and identity hash code into a single long value
        return (((long) contentHashCode) << 32) | (identityHashCode & 0xffffffffL);
    }

    private Map<Long, WeakReference<CharSequence>> getEncodedCharSequencesForEncoder(Encoder encoder) {
        Map<Long, WeakReference<CharSequence>> encodedCharSequences = encodedCharSequencesForEncoder.get(encoder);
        if (encodedCharSequences == null) {
            encodedCharSequences = new HashMap<>();
            encodedCharSequencesForEncoder.put(encoder, encodedCharSequences);
        }
        return encodedCharSequences;
    }

    /* (non-Javadoc)
     * @see EncodingStateRegistry#getEncodingStateFor(java.lang.CharSequence)
     */
    public EncodingState getEncodingStateFor(CharSequence string) {
        Long key = calculateKey(string);
        Set<Encoder> result = null;
        for (Map.Entry<Encoder, Map<Long, WeakReference<CharSequence>>> entry : encodedCharSequencesForEncoder.entrySet()) {
            WeakReference<CharSequence> charSequenceReference = entry.getValue().get(key);
            if (charSequenceReference != null && string == charSequenceReference.get()) {
                if (result == null) {
                    result = Collections.singleton(entry.getKey());
                } else {
                    if (result.size() == 1) {
                        result = new HashSet<Encoder>(result);
                    }
                    result.add(entry.getKey());
                }
            }
        }
        return result != null ? new EncodingStateImpl(result, null) : EncodingStateImpl.UNDEFINED_ENCODING_STATE;
    }

    /* (non-Javadoc)
     * @see EncodingStateRegistry#isEncodedWith(Encoder, java.lang.CharSequence)
     */
    public boolean isEncodedWith(Encoder encoder, CharSequence string) {
        return getEncodedCharSequencesForEncoder(encoder).containsKey(calculateKey(string));
    }

    /* (non-Javadoc)
     * @see EncodingStateRegistry#registerEncodedWith(Encoder, java.lang.CharSequence)
     */
    public void registerEncodedWith(Encoder encoder, CharSequence escaped) {
        WeakReference<CharSequence> previousValue = getEncodedCharSequencesForEncoder(encoder).put(calculateKey(escaped), new WeakReference<>(escaped));
        if (previousValue != null && previousValue.get() != escaped) {
            LOG.warn("Hash collision for encoded value between '{}' and '{}', encoder is {}", escaped, previousValue.get(), encoder);
        }
    }

    /* (non-Javadoc)
     * @see EncodingStateRegistry#shouldEncodeWith(Encoder, java.lang.CharSequence)
     */
    public boolean shouldEncodeWith(Encoder encoderToApply, CharSequence string) {
        if (isNoneEncoder(encoderToApply)) return false;
        EncodingState encodingState = getEncodingStateFor(string);
        return shouldEncodeWith(encoderToApply, encodingState);
    }

    /**
     * Checks if encoder should be applied to a input with given encoding state
     *
     * @param encoderToApply       the encoder to apply
     * @param currentEncodingState the current encoding state
     * @return true, if should encode
     */
    public static boolean shouldEncodeWith(Encoder encoderToApply, EncodingState currentEncodingState) {
        if (isNoneEncoder(encoderToApply)) return false;
        if (currentEncodingState != null && currentEncodingState.getEncoders() != null) {
            for (Encoder encoder : currentEncodingState.getEncoders()) {
                if (isPreviousEncoderSafeOrEqual(encoderToApply, encoder)) {
                    return false;
                }
            }
        }
        return true;
    }

    public static boolean isNoneEncoder(Encoder encoderToApply) {
        return encoderToApply == null || encoderToApply == NONE_ENCODER || encoderToApply.getClass() == NoneEncoder.class;
    }

    /**
     * Checks if is previous encoder is already "safe", equal or equivalent
     *
     * @param encoderToApply  the encoder to apply
     * @param previousEncoder the previous encoder
     * @return true, if previous encoder is already "safe", equal or equivalent
     */
    public static boolean isPreviousEncoderSafeOrEqual(Encoder encoderToApply, Encoder previousEncoder) {
        return previousEncoder == encoderToApply || !encoderToApply.isApplyToSafelyEncoded() && previousEncoder.isSafe() && encoderToApply.isSafe()
                || previousEncoder.getCodecIdentifier().isEquivalent(encoderToApply.getCodecIdentifier());
    }
}
