package org.grails.orm.hibernate.cfg.domainbinding;

import java.util.Iterator;
import java.util.Objects;

import org.hibernate.FetchMode;
import org.hibernate.MappingException;
import org.hibernate.boot.model.internal.AnnotatedJoinColumn;
import org.hibernate.boot.model.internal.AnnotatedJoinColumns;
import org.hibernate.boot.registry.classloading.spi.ClassLoaderService;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.internal.util.ReflectHelper;
import org.hibernate.mapping.Component;
import org.hibernate.mapping.Fetchable;
import org.hibernate.mapping.ForeignKey;
import org.hibernate.mapping.Join;
import org.hibernate.mapping.KeyValue;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.SimpleValue;
import org.hibernate.mapping.SortableValue;
import org.hibernate.mapping.ToOne;
import org.hibernate.mapping.Value;
import org.hibernate.type.EntityType;
import org.hibernate.type.ForeignKeyDirection;
import org.hibernate.type.MappingContext;
import org.hibernate.type.Type;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Selectable;
import org.hibernate.mapping.Property;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.hibernate.engine.spi.SharedSessionContractImplementor;

import org.grails.orm.hibernate.cfg.Table;

import static org.hibernate.boot.model.internal.BinderHelper.findReferencedColumnOwner;


/**
 * Grails interface for Hibernate ToOne mapping.
 * This interface declares the public API expected from a Grails abstraction of ToOne,
 * intended to be implemented by concrete wrapper classes.
 */
public interface GrailsToOne extends KeyValue, Fetchable, SortableValue {


}
