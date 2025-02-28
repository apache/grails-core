package org.grails.plugins.codecs;

import org.grails.encoder.impl.JSONCodecFactory;
import grails.encoders.JSONEncoder;

/**
 * A codec that encodes strings to JSON
 *
 * @author Lari Hotari
 * @since 2.3.4
 */
public class JSONCodec extends JSONCodecFactory {
    public JSONCodec() {
        super();
        setEncoder(new JSONEncoder());
    }
}
