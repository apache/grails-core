package org.grails.orm.hibernate.cfg.domainbinding;

import java.util.List;
import java.util.Set;

import org.hibernate.FetchMode;
import org.hibernate.MappingException;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.dialect.Dialect;
import org.hibernate.generator.Generator;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.ForeignKey;
import org.hibernate.mapping.GeneratorSettings;
import org.hibernate.mapping.OneToOne;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Property;
import org.hibernate.mapping.RootClass;
import org.hibernate.mapping.Selectable;
import org.hibernate.mapping.Table;
import org.hibernate.mapping.Value;
import org.hibernate.mapping.ValueVisitor;
import org.hibernate.metamodel.mapping.JdbcMapping;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.type.MappingContext;
import org.hibernate.type.Type;

public class GrailsOneToOne implements GrailsToOne {
    private final OneToOne delegate;

    public GrailsOneToOne(MetadataBuildingContext buildingContext, Table table, PersistentClass owner) throws MappingException {
        this.delegate = new OneToOne(buildingContext,table, owner);
    }

    @Override
    public void setFetchMode(FetchMode joinedFetch) {
        delegate.setFetchMode(joinedFetch);
    }

    @Override
    public boolean isLazy() {
        return delegate.isLazy();
    }

    @Override
    public void setLazy(boolean lazy) {
        delegate.setLazy(lazy);
    }

    @Override
    public ForeignKey createForeignKeyOfEntity(String entityName, List<Column> referencedColumns) {
        return delegate.createForeignKeyOfEntity(entityName, referencedColumns);
    }

    @Override
    public ForeignKey createForeignKeyOfEntity(String entityName) {
        return delegate.createForeignKeyOfEntity(entityName);
    }

    @Override
    public boolean isCascadeDeleteEnabled() {
        return delegate.isCascadeDeleteEnabled();
    }

    @Override
    public NullValueSemantic getNullValueSemantic() {
        return delegate.getNullValueSemantic();
    }

    @Override
    public String getNullValue() {
        return delegate.getNullValue();
    }

    @Override
    public boolean isUpdateable() {
        return delegate.isUpdateable();
    }

    @Override
    public Generator createGenerator(Dialect dialect, RootClass rootClass) {
        return delegate.createGenerator(dialect, rootClass);
    }

    @Override
    public Generator createGenerator(Dialect dialect, RootClass rootClass, Property property, GeneratorSettings defaults) {
        return delegate.createGenerator(dialect, rootClass, property, defaults);
    }

    @Override
    public boolean isSorted() {
        return delegate.isSorted();
    }

    @Override
    public int[] sortProperties() {
        return delegate.sortProperties();
    }

    @Override
    public int getColumnSpan() {
        return delegate.getColumnSpan();
    }

    @Override
    public List<Selectable> getSelectables() {
        return delegate.getSelectables();
    }

    @Override
    public List<Column> getColumns() {
        return delegate.getColumns();
    }

    @Override
    public List<Selectable> getVirtualSelectables() {
        return delegate.getVirtualSelectables();
    }

    @Override
    public List<Column> getConstraintColumns() {
        return delegate.getConstraintColumns();
    }

    @Override
    public Type getType() throws MappingException {
        return delegate.getType();
    }

    @Override
    public JdbcMapping getSelectableType(MappingContext mappingContext, int index) throws MappingException {
        return delegate.getSelectableType(mappingContext, index);
    }

    @Override
    public FetchMode getFetchMode() {
        return delegate.getFetchMode();
    }

    @Override
    public Table getTable() {
        return delegate.getTable();
    }

    @Override
    public boolean hasFormula() {
        return delegate.hasFormula();
    }

    @Override
    public boolean isAlternateUniqueKey() {
        return delegate.isAlternateUniqueKey();
    }

    @Override
    public boolean isPartitionKey() {
        return delegate.isPartitionKey();
    }

    @Override
    public boolean isNullable() {
        return delegate.isNullable();
    }

    @Override
    public void createForeignKey() {
        delegate.createForeignKey();
    }

    @Override
    public void createUniqueKey(MetadataBuildingContext context) {
        delegate.createUniqueKey(context);
    }

    @Override
    public boolean isSimpleValue() {
        return delegate.isSimpleValue();
    }

    @Override
    public boolean isValid(MappingContext mappingContext) throws MappingException {
        return delegate.isValid(mappingContext);
    }

    @Override
    public void setTypeUsingReflection(String className, String propertyName) throws MappingException {
        delegate.setTypeUsingReflection(className, propertyName);
    }

    @Override
    public Object accept(ValueVisitor visitor) {
        return delegate.accept(visitor);
    }

    @Override
    public boolean isSame(Value other) {
        return delegate.isSame(other);
    }

    @Override
    public boolean[] getColumnInsertability() {
        return delegate.getColumnInsertability();
    }

    @Override
    public boolean hasAnyInsertableColumns() {
        return delegate.hasAnyInsertableColumns();
    }

    @Override
    public boolean[] getColumnUpdateability() {
        return delegate.getColumnUpdateability();
    }

    @Override
    public boolean hasAnyUpdatableColumns() {
        return delegate.hasAnyUpdatableColumns();
    }

    @Override
    public MetadataBuildingContext getBuildingContext() {
        return delegate.getBuildingContext();
    }

    @Override
    public ServiceRegistry getServiceRegistry() {
        return delegate.getServiceRegistry();
    }

    @Override
    public Value copy() {
        return delegate.copy();
    }

    @Override
    public boolean isColumnInsertable(int index) {
        return delegate.isColumnInsertable(index);
    }

    @Override
    public boolean isColumnUpdateable(int index) {
        return delegate.isColumnUpdateable(index);
    }

    @Override
    public String getExtraCreateTableInfo() {
        return delegate.getExtraCreateTableInfo();
    }

    @Override
    public void checkColumnDuplication(Set<String> distinctColumns, String owner) {
        delegate.checkColumnDuplication(distinctColumns, owner);
    }
}
