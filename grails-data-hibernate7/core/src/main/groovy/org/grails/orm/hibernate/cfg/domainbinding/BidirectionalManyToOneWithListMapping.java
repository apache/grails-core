package org.grails.orm.hibernate.cfg.domainbinding;

import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.types.Association;
import org.hibernate.mapping.ManyToOne;
import org.hibernate.mapping.Property;

import java.util.List;

public class BidirectionalManyToOneWithListMapping {

    public boolean isBidirectionalManyToOneWithListMapping(PersistentProperty<?> grailsProperty, Property prop) {
        if(grailsProperty instanceof Association<?> association) {

            return association.isBidirectional()
                    && association.getInverseSide() != null
                    && List.class.isAssignableFrom(association.getInverseSide().getType())
                    && prop != null
                    && prop.getValue() instanceof ManyToOne;

        }
        return false;
    }
}
