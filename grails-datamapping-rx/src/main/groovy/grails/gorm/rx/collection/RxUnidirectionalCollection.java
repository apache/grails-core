package grails.gorm.rx.collection;

import java.io.Serializable;
import java.util.List;

/**
 * Implemented by collections that store the inverse association keys to perform a lookup
 *
 * @author Graeme
 * @since 6.0
 */
public interface RxUnidirectionalCollection {
    /**
     * @return The association keys in the case of unidirectional one-to-many
     */
    List<Serializable> getAssociationKeys();

}
