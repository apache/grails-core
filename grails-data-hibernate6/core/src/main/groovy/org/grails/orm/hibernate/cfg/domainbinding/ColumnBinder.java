package org.grails.orm.hibernate.cfg.domainbinding;

import org.hibernate.mapping.Column;
import org.hibernate.mapping.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.model.types.ManyToMany;
import org.grails.datastore.mapping.model.types.OneToOne;
import org.grails.datastore.mapping.model.types.ToOne;
import org.grails.orm.hibernate.cfg.ColumnConfig;
import org.grails.orm.hibernate.cfg.Mapping;
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;
import org.grails.orm.hibernate.cfg.PropertyConfig;

public class ColumnBinder {

    private static final Logger LOG = LoggerFactory.getLogger(ColumnBinder.class);

   private final ColumnNameForPropertyAndPathFetcher columnNameForPropertyAndPathFetcher;
   private final PersistentPropertyToPropertyConfig persistentPropertyToPropertyConfig;
   private final StringColumnConstraintsBinder stringColumnConstraintsBinder;
   private final NumericColumnConstraintsBinder numericColumnConstraintsBinder;
   private final CreateKeyForProps createKeyForProps;
   private final HibernateEntityWrapper hibernateEntityWrapper;
   private final UserTypeFetcher userTypeFetcher;
   private final IndexBinder indexBinder;

   public ColumnBinder(PersistentEntityNamingStrategy namingStrategy) {
       this.columnNameForPropertyAndPathFetcher = new ColumnNameForPropertyAndPathFetcher(namingStrategy);
       this.persistentPropertyToPropertyConfig = new PersistentPropertyToPropertyConfig();
       this.stringColumnConstraintsBinder = new StringColumnConstraintsBinder();
       this.numericColumnConstraintsBinder = new NumericColumnConstraintsBinder();
       this.createKeyForProps = new CreateKeyForProps(columnNameForPropertyAndPathFetcher);
       this.hibernateEntityWrapper = new HibernateEntityWrapper();
       this.userTypeFetcher = new UserTypeFetcher();
       this.indexBinder = new IndexBinder();
   }
   protected ColumnBinder(ColumnNameForPropertyAndPathFetcher columnNameForPropertyAndPathFetcher
   , PersistentPropertyToPropertyConfig persistentPropertyToPropertyConfig
   , StringColumnConstraintsBinder stringColumnConstraintsBinder
   , NumericColumnConstraintsBinder numericColumnConstraintsBinder
   , CreateKeyForProps createKeyForProps
   , HibernateEntityWrapper hibernateEntityWrapper
   , UserTypeFetcher userTypeFetcher
   , IndexBinder indexBinder) {
       this.columnNameForPropertyAndPathFetcher = columnNameForPropertyAndPathFetcher;
       this.persistentPropertyToPropertyConfig = persistentPropertyToPropertyConfig;
       this.stringColumnConstraintsBinder = stringColumnConstraintsBinder;
       this.numericColumnConstraintsBinder =  numericColumnConstraintsBinder;
       this.createKeyForProps = createKeyForProps;
       this.hibernateEntityWrapper = hibernateEntityWrapper;
       this.userTypeFetcher = userTypeFetcher;
       this.indexBinder = indexBinder;
   }
    /**
     * Binds a Column instance to the Hibernate meta model
     *
     * @param property       The Grails domain class property
     * @param parentProperty
     * @param column         The column to bind
     * @param path
     * @param table          The table name
     */
    public void bindColumn(PersistentProperty property, PersistentProperty parentProperty,
                           Column column, ColumnConfig cc, String path, Table table) {

        if (cc != null) {
            column.setComment(cc.getComment());
            column.setDefaultValue(cc.getDefaultValue());
            column.setCustomRead(cc.getRead());
            column.setCustomWrite(cc.getWrite());
        }

        Class<?> userType = userTypeFetcher.getUserType(property);
        String columnName = columnNameForPropertyAndPathFetcher.getColumnNameForPropertyAndPath(property, path, cc);
        if ((property instanceof Association association) && userType == null) {
            // Only use conventional naming when the column has not been explicitly mapped.
            if (column.getName() == null) {
                column.setName(columnName);
            }
            if (property instanceof ManyToMany) {
                column.setNullable(false);
            }
            else if (property instanceof OneToOne && association.isBidirectional() && !association.isOwningSide()) {
                if (association.getInverseSide().isHasOne()) {
                    column.setNullable(false);
                }
                else {
                    column.setNullable(true);
                }
            }
            else if ((property instanceof ToOne) && association.isCircular()) {
                column.setNullable(true);
            }
            else {
                column.setNullable(property.isNullable());
            }
        }
        else {
            column.setName(columnName);
            column.setNullable(property.isNullable() || (parentProperty != null && parentProperty.isNullable()));
            // We'll reuse the same PropertyConfig for any constraints and uniqueness
            PropertyConfig mappedForm = null;
            // Use the constraints for this property to more accurately define
            // the column's length, precision, and scale
            Class<?> type = property.getType();
            if (type != null && (String.class.isAssignableFrom(type) || byte[].class.isAssignableFrom(type))) {
                if (mappedForm == null) mappedForm = persistentPropertyToPropertyConfig.apply(property);
                stringColumnConstraintsBinder.bindStringColumnConstraints(column, mappedForm);
            } else if (type != null && Number.class.isAssignableFrom(type)) {
                if (mappedForm == null) mappedForm = persistentPropertyToPropertyConfig.apply(property);
                numericColumnConstraintsBinder.bindNumericColumnConstraints(column, cc, mappedForm);
            }
        }

        createKeyForProps.createKeyForProps(property, path, table, columnName);
        indexBinder.bindIndex(columnName, column, cc, table);

        final PersistentEntity owner = property.getOwner();
        if (!owner.isRoot()) {
            Mapping mapping = hibernateEntityWrapper.getMappedForm(owner);
            if (mapping.getTablePerHierarchy()) {
                if (LOG.isDebugEnabled())
                    LOG.debug("[GrailsDomainBinder] Sub class property [" + property.getName() + "] for column name ["+column.getName()+"] set to nullable");
                column.setNullable(true);
            } else {
                column.setNullable(property.isNullable());
            }
        }

        // Apply uniqueness last to ensure it isn't overridden by downstream binders
        PropertyConfig mappedFormFinal = persistentPropertyToPropertyConfig.apply(property);
        column.setUnique(mappedFormFinal.isUnique() && !mappedFormFinal.isUniqueWithinGroup());

        if (LOG.isDebugEnabled())
            LOG.debug("[GrailsDomainBinder] bound property [" + property.getName() + "] to column name ["+column.getName()+"] in table ["+table.getName()+"]");
    }
}
