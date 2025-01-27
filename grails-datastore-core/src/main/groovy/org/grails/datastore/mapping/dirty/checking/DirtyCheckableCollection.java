package org.grails.datastore.mapping.dirty.checking;

import java.io.Serializable;

/**
 * @author Graeme Rocher
 * @since 4.1
 *
 */
public interface DirtyCheckableCollection extends Serializable {

    /**
     * @return True if the collection has changed
     */
    boolean hasChanged();

    /**
     * @return The previous size of the collection prior to any changes
     */
    int getOriginalSize();

    /**
     * @return True if the collection has grown
     */
    boolean hasGrown();

    /**
     *
     * @return True if the collection has shrunk
     */
    boolean hasShrunk();

    /**
     * @return True if the collection has changed size
     */
    boolean hasChangedSize();
}
