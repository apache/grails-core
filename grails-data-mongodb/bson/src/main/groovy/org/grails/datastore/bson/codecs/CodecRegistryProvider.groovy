package org.grails.datastore.bson.codecs

import org.bson.codecs.configuration.CodecRegistry

/**
 * An interface for objects that provide a codec registry
 *
 * @author Graeme Rocher
 * @since 6.0
 */
interface CodecRegistryProvider {
    CodecRegistry getCodecRegistry()
}