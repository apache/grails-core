package grails.gorm.rx.collection;

import org.grails.datastore.mapping.collection.PersistentCollection;

import java.io.Serializable;
import java.util.List;

/**
 * Represents a reactive collection
 *
 * @author Graeme Rocher
 * @since 6.0
 */
public interface RxPersistentCollection<T> extends PersistentCollection, ObservableCollection<T> {


}
