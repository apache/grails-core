/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package grails.gorm.tests.generated

import grails.gorm.tests.AddToManagedEntitySpec
import grails.gorm.tests.AutoTimestampSpec
import grails.gorm.tests.CascadeToBidirectionalAsssociationSpec
import grails.gorm.tests.CountByWithEmbeddedSpec
import grails.gorm.tests.EnumMappingSpec
import grails.gorm.tests.FetchJoinSpec
import grails.gorm.tests.Hibernate7OptimisticLockingSpec
import grails.gorm.tests.HibernateGetAllConvertibleIdSpec
import grails.gorm.tests.HibernateValidationSpec
import grails.gorm.tests.LastUpdateWithDynamicUpdateSpec
import grails.gorm.tests.ManagedEntitySaveSpec
import grails.gorm.tests.ManyToOneSpec
import grails.gorm.tests.MultiColumnUniqueConstraintSpec
import grails.gorm.tests.PagedResultListSpec
import grails.gorm.tests.RLikeSpec
import grails.gorm.tests.ReadOperationSpec
import grails.gorm.tests.SizeConstraintSpec
import grails.gorm.tests.SortArgumentValidationSpec
import grails.gorm.tests.TablePerSubClassAndEmbeddedSpec
import grails.gorm.tests.ToOneProxySpec
import grails.gorm.tests.WhereQueryWithAssociationSortSpec
import grails.gorm.tests.autoimport.AutoImportSpec
import grails.gorm.tests.belongsto.BidirectionalOneToOneWithUniqueSpec
import grails.gorm.tests.detachedcriteria.DetachCriteriaSubquerySpec
import grails.gorm.tests.detachedcriteria.DetachedCriteriaCountSpec
import grails.gorm.tests.detachedcriteria.DetachedCriteriaJoinSpec
import grails.gorm.tests.detachedcriteria.DetachedCriteriaProjectionSpec
import grails.gorm.tests.dirtychecking.DirtyCheckingSpecHibernate7
import grails.gorm.tests.dirtychecking.HibernateDirtyCheckingSpec
import grails.gorm.tests.hasmany.ListCollectionSpec
import grails.gorm.tests.hasmany.TwoUnidirectionalHasManySpec
import grails.gorm.tests.hibernatequery.HibernateAssociationQuerySpec
import grails.gorm.tests.inheritance.IntermediateSubclassQuerySpec
import grails.gorm.tests.inheritance.SubclassToOneProxySpec
import grails.gorm.tests.inheritance.TablePerConcreteClassAndDateCreatedSpec
import grails.gorm.tests.inheritance.TablePerConcreteClassImportedSpec
import grails.gorm.tests.mappedby.MultipleOneToOneSpec
import grails.gorm.tests.proxy.ByteBuddyProxySpec
import grails.gorm.tests.proxy.Hibernate7GroovyProxySpec
import grails.gorm.tests.traits.InterfacePropertySpec
import grails.gorm.tests.validation.BeanValidationSpec
import grails.gorm.tests.validation.DeepValidationSpec
import grails.gorm.tests.validation.UniqueWithHasOneSpec
import org.apache.grails.data.testing.tck.tests.AttachMethodSpec
import org.apache.grails.data.testing.tck.tests.CircularOneToManySpec
import org.apache.grails.data.testing.tck.tests.CommonTypesPersistenceSpec
import org.apache.grails.data.testing.tck.tests.ConstraintsSpec
import org.apache.grails.data.testing.tck.tests.CriteriaBuilderSpec
import org.apache.grails.data.testing.tck.tests.CrudOperationsSpec
import org.apache.grails.data.testing.tck.tests.DeleteAllSpec
import org.apache.grails.data.testing.tck.tests.DetachedCriteriaSpec
import org.apache.grails.data.testing.tck.tests.DirtyCheckingSpec
import org.apache.grails.data.testing.tck.tests.DisableAutotimeStampSpec
import org.apache.grails.data.testing.tck.tests.DomainEventsSpec
import org.apache.grails.data.testing.tck.tests.EnumSpec
import org.apache.grails.data.testing.tck.tests.FindByExampleSpec
import org.apache.grails.data.testing.tck.tests.FindByMethodSpec
import org.apache.grails.data.testing.tck.tests.FindOrCreateWhereSpec
import org.apache.grails.data.testing.tck.tests.FindOrSaveWhereSpec
import org.apache.grails.data.testing.tck.tests.FindWhereSpec
import org.apache.grails.data.testing.tck.tests.FirstAndLastMethodSpec
import org.apache.grails.data.testing.tck.tests.GormEnhancerSpec
import org.apache.grails.data.testing.tck.tests.GormValidateableSpec
import org.apache.grails.data.testing.tck.tests.InheritanceSpec
import org.apache.grails.data.testing.tck.tests.ListOrderBySpec
import org.apache.grails.data.testing.tck.tests.NegationSpec
import org.apache.grails.data.testing.tck.tests.NotInListSpec
import org.apache.grails.data.testing.tck.tests.NullValueEqualSpec
import org.apache.grails.data.testing.tck.tests.OneToManySpec
import org.apache.grails.data.testing.tck.tests.OneToOneSpec
import org.apache.grails.data.testing.tck.tests.OptimisticLockingSpec
import org.apache.grails.data.testing.tck.tests.OrderBySpec
import org.apache.grails.data.testing.tck.tests.PagedResultSpec
import org.apache.grails.data.testing.tck.tests.PersistenceEventListenerSpec
import org.apache.grails.data.testing.tck.tests.ProxyInitializationSpec
import org.apache.grails.data.testing.tck.tests.ProxyLoadingSpec
import org.apache.grails.data.testing.tck.tests.QueryAfterPropertyChangeSpec
import org.apache.grails.data.testing.tck.tests.QueryByAssociationSpec
import org.apache.grails.data.testing.tck.tests.QueryByNullSpec
import org.apache.grails.data.testing.tck.tests.QueryEventsSpec
import org.apache.grails.data.testing.tck.tests.RangeQuerySpec
import org.apache.grails.data.testing.tck.tests.SaveAllSpec
import org.apache.grails.data.testing.tck.tests.SessionPropertiesSpec
import org.apache.grails.data.testing.tck.tests.SizeQuerySpec
import org.apache.grails.data.testing.tck.tests.StaticCompiledCriteriaSpec
import org.apache.grails.data.testing.tck.tests.UniqueConstraintSpec
import org.apache.grails.data.testing.tck.tests.UpdateWithProxyPresentSpec
import org.apache.grails.data.testing.tck.tests.ValidationSpec
import org.apache.grails.data.testing.tck.tests.WhereLazySpec
import org.apache.grails.data.testing.tck.tests.WhereQueryEmbeddedSpec
import org.apache.grails.data.testing.tck.tests.WhereQueryIssueVerificationSpec
import org.apache.grails.data.testing.tck.tests.WhereQueryLeftJoinSpec
import org.apache.grails.data.testing.tck.tests.WithTransactionSpec

/**
 * Existing specs of the module and of the TCK run unchanged against a datastore booted through the generated-domain-class
 * path: each subclass inherits every feature of its parent and only switches the harness to
 * {@code registerGeneratedDomainClasses}. The domain classes the parent registered are the ones that are generated.
 */

class GeneratedAddToManagedEntitySpec extends AddToManagedEntitySpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedAutoTimestampSpec extends AutoTimestampSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedCascadeToBidirectionalAsssociationSpec extends CascadeToBidirectionalAsssociationSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedCountByWithEmbeddedSpec extends CountByWithEmbeddedSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedEnumMappingSpec extends EnumMappingSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedFetchJoinSpec extends FetchJoinSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedHibernate7OptimisticLockingSpec extends Hibernate7OptimisticLockingSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedHibernateValidationSpec extends HibernateValidationSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedLastUpdateWithDynamicUpdateSpec extends LastUpdateWithDynamicUpdateSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedManagedEntitySaveSpec extends ManagedEntitySaveSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedManyToOneSpec extends ManyToOneSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedReadOperationSpec extends ReadOperationSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedTablePerSubClassAndEmbeddedSpec extends TablePerSubClassAndEmbeddedSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedToOneProxySpec extends ToOneProxySpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedDetachCriteriaSubquerySpec extends DetachCriteriaSubquerySpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedDetachedCriteriaCountSpec extends DetachedCriteriaCountSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedDetachedCriteriaJoinSpec extends DetachedCriteriaJoinSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedDetachedCriteriaProjectionSpec extends DetachedCriteriaProjectionSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedDirtyCheckingSpecHibernate7 extends DirtyCheckingSpecHibernate7 {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedHibernateDirtyCheckingSpec extends HibernateDirtyCheckingSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedListCollectionSpec extends ListCollectionSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedIntermediateSubclassQuerySpec extends IntermediateSubclassQuerySpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedSubclassToOneProxySpec extends SubclassToOneProxySpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedByteBuddyProxySpec extends ByteBuddyProxySpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedHibernate7GroovyProxySpec extends Hibernate7GroovyProxySpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedAttachMethodSpec extends AttachMethodSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedCircularOneToManySpec extends CircularOneToManySpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedCommonTypesPersistenceSpec extends CommonTypesPersistenceSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedCriteriaBuilderSpec extends CriteriaBuilderSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedCrudOperationsSpec extends CrudOperationsSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedDeleteAllSpec extends DeleteAllSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedDetachedCriteriaSpec extends DetachedCriteriaSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedDirtyCheckingSpec extends DirtyCheckingSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedDomainEventsSpec extends DomainEventsSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedEnumSpec extends EnumSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedFindByExampleSpec extends FindByExampleSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedFindByMethodSpec extends FindByMethodSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedFindWhereSpec extends FindWhereSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedInheritanceSpec extends InheritanceSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedListOrderBySpec extends ListOrderBySpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedNegationSpec extends NegationSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedNotInListSpec extends NotInListSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedOneToManySpec extends OneToManySpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedOneToOneSpec extends OneToOneSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedOrderBySpec extends OrderBySpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedPagedResultSpec extends PagedResultSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedPersistenceEventListenerSpec extends PersistenceEventListenerSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedProxyInitializationSpec extends ProxyInitializationSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedProxyLoadingSpec extends ProxyLoadingSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedQueryAfterPropertyChangeSpec extends QueryAfterPropertyChangeSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedQueryByAssociationSpec extends QueryByAssociationSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedQueryByNullSpec extends QueryByNullSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedRangeQuerySpec extends RangeQuerySpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedSaveAllSpec extends SaveAllSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedUpdateWithProxyPresentSpec extends UpdateWithProxyPresentSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedValidationSpec extends ValidationSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedWhereLazySpec extends WhereLazySpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedWithTransactionSpec extends WithTransactionSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedHibernateGetAllConvertibleIdSpec extends HibernateGetAllConvertibleIdSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}


class GeneratedMultiColumnUniqueConstraintSpec extends MultiColumnUniqueConstraintSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedPagedResultListSpec extends PagedResultListSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedRLikeSpec extends RLikeSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedSizeConstraintSpec extends SizeConstraintSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedSortArgumentValidationSpec extends SortArgumentValidationSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedWhereQueryWithAssociationSortSpec extends WhereQueryWithAssociationSortSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedAutoImportSpec extends AutoImportSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedBidirectionalOneToOneWithUniqueSpec extends BidirectionalOneToOneWithUniqueSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedTwoUnidirectionalHasManySpec extends TwoUnidirectionalHasManySpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedHibernateAssociationQuerySpec extends HibernateAssociationQuerySpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedTablePerConcreteClassAndDateCreatedSpec extends TablePerConcreteClassAndDateCreatedSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedTablePerConcreteClassImportedSpec extends TablePerConcreteClassImportedSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedMultipleOneToOneSpec extends MultipleOneToOneSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedInterfacePropertySpec extends InterfacePropertySpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedBeanValidationSpec extends BeanValidationSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedDeepValidationSpec extends DeepValidationSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedUniqueWithHasOneSpec extends UniqueWithHasOneSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedConstraintsSpec extends ConstraintsSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedDisableAutotimeStampSpec extends DisableAutotimeStampSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedFindOrCreateWhereSpec extends FindOrCreateWhereSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedFindOrSaveWhereSpec extends FindOrSaveWhereSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedFirstAndLastMethodSpec extends FirstAndLastMethodSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedGormEnhancerSpec extends GormEnhancerSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedGormValidateableSpec extends GormValidateableSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedNullValueEqualSpec extends NullValueEqualSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedOptimisticLockingSpec extends OptimisticLockingSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedQueryEventsSpec extends QueryEventsSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedSessionPropertiesSpec extends SessionPropertiesSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedSizeQuerySpec extends SizeQuerySpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedStaticCompiledCriteriaSpec extends StaticCompiledCriteriaSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedUniqueConstraintSpec extends UniqueConstraintSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedWhereQueryEmbeddedSpec extends WhereQueryEmbeddedSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedWhereQueryIssueVerificationSpec extends WhereQueryIssueVerificationSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}

class GeneratedWhereQueryLeftJoinSpec extends WhereQueryLeftJoinSpec {

    void setupSpec() {
        manager.registerGeneratedDomainClasses()
    }
}
