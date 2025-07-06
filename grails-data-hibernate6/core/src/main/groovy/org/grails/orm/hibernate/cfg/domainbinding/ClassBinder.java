package org.grails.orm.hibernate.cfg.domainbinding;

import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.orm.hibernate.cfg.Mapping;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.mapping.PersistentClass;

import static org.grails.orm.hibernate.cfg.GrailsHibernateUtil.unqualify;

public class ClassBinder {

    /**
     * Binds the specified persistant class to the runtime model based on the
     * properties defined in the domain class
     *
     * @param persistentEntity     The Grails domain class
     * @param persistentClass The persistant class
     * @param collector        Existing collector
     */
    public void bindClass(PersistentEntity persistentEntity, PersistentClass persistentClass, InFlightMetadataCollector collector) {
        persistentClass.setLazy(true);
        var entityName = persistentEntity.getName();
        persistentClass.setEntityName(entityName);
        persistentClass.setJpaEntityName(entityName);
        persistentClass.setProxyInterfaceName(entityName);
        persistentClass.setClassName(entityName);
        persistentClass.setDynamicInsert(false);
        persistentClass.setDynamicUpdate(false);
        persistentClass.setSelectBeforeUpdate(false);

        boolean autoImport;
        if (persistentEntity.getMapping().getMappedForm() instanceof Mapping mappedForm) {
            autoImport = mappedForm.isAutoImport();
        } else {
            autoImport = collector.getMetadataBuildingOptions().getMappingDefaults().isAutoImportEnabled();
        }
        if (autoImport) {
            String unqualified = unqualify(entityName);
            persistentClass.setJpaEntityName(unqualified);
            collector.addImport(unqualified, entityName);
        }
    }
}
