package org.grails.datastore.rx.exceptions

/**
 * Thrown when a blocking operation is executed
 *
 * @author Graeme Rocher
 * @since 6.0
 */
class BlockingOperationException extends RuntimeException {

    BlockingOperationException(String var1) {
        super(var1)
    }
}
