package org.grails.orm.hibernate.cfg.domainbinding;

import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.types.ToOne;
// each property may consist of one or many columns (due to composite ids) so in order to get the
// number of columns required for a column key we have to perform the calculation here
public class ForeignKeyColumnCountCalculator {
    public int calculateForeignKeyColumnCount(PersistentEntity refDomainClass, String[] propertyNames) {
        int expectedForeignKeyColumnLength = 0;
        for (String propertyName : propertyNames) {
            PersistentProperty referencedProperty = refDomainClass.getPropertyByName(propertyName);
            if(referencedProperty instanceof ToOne toOne) {
                PersistentProperty[] compositeIdentity = toOne.getAssociatedEntity().getCompositeIdentity();
                if(compositeIdentity != null) {
                    expectedForeignKeyColumnLength += compositeIdentity.length;
                }
                else {
                    expectedForeignKeyColumnLength++;
                }
            }
            else {
                expectedForeignKeyColumnLength++;
            }
        }
        return expectedForeignKeyColumnLength;
    }
}
