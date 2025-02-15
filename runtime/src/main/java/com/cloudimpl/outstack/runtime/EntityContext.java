/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.cloudimpl.outstack.runtime;

import com.cloudimpl.outstack.runtime.domainspec.ChildEntity;
import com.cloudimpl.outstack.runtime.domainspec.DomainEventException;
import com.cloudimpl.outstack.runtime.domainspec.Entity;
import com.cloudimpl.outstack.runtime.domainspec.Event;
import com.cloudimpl.outstack.runtime.domainspec.RootEntity;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 *
 * @author nuwansa
 * @param <T>
 */
public abstract class EntityContext<T extends Entity> implements Context {

    private final String tenantId;
    protected final Class<T> entityType;
    private final List<Event> events;
    protected final Optional<EntityProvider<? extends RootEntity>> entitySupplier;
    protected final Supplier<String> idGenerator;
    protected final Optional<CRUDOperations> crudOperations;
    protected final QueryOperations<?> queryOperation;
    protected final Optional<Consumer<Event>> eventPublisher;
    protected ITransaction tx;
    protected final Consumer<Object> validator;
    protected final Function<Class<? extends RootEntity>, QueryOperations<?>> queryOperationSelector;
    protected final String version;
    protected EntityMetaDetail entityMeta;

    public EntityContext(Class<T> entityType, String tenantId, Optional<EntityProvider<? extends RootEntity>> entitySupplier, Supplier<String> idGenerator, Optional<CRUDOperations> crudOperations,
            QueryOperations<?> queryOperation, Optional<Consumer<Event>> eventPublisher, Consumer<Object> validator,
            Function<Class<? extends RootEntity>, QueryOperations<?>> queryOperationSelector, String version) {
        this.tenantId = tenantId;
        this.events = new LinkedList<>();
        this.entityType = entityType;
        this.entitySupplier = entitySupplier;
        this.idGenerator = idGenerator;
        this.crudOperations = crudOperations;
        this.eventPublisher = eventPublisher;
        this.queryOperation = queryOperation;
        this.validator = validator;
        this.queryOperationSelector = queryOperationSelector;
        this.version = version;
        this.entityMeta = EntityMetaDetailCache.instance().getEntityMeta(entityType);
    }

    public ITransaction getTx() {
        return this.tx;
    }

    protected String getVersion() {
        return version;
    }

    protected <R extends RootEntity> EntityProvider<R> getEntityProvider() {
        return (EntityProvider<R>) entitySupplier.get();
    }

    protected <R extends RootEntity> QueryOperations<R> getQueryOperations() {
        return (QueryOperations<R>) queryOperation;
    }

    public <X extends EntityContext> X setTx(ITransaction tx) {
        this.tx = tx;
        return (X)this;
    }

    public Consumer<Event> getEventPublisher() {
        return this.eventPublisher.get();
    }

    public String getTenantId() {
        return tenantId;
    }

    public List<Event> getEvents() {
        return this.events;
    }

    protected CRUDOperations getCrudOperations() {
        return crudOperations.get();
    }

    protected void addEvent(Event<T> event) {
        this.events.add(event);
    }

    protected EntityMetaDetail getEntityMeta() {
        return entityMeta;
    }

    public abstract T create(String id, Event<T> event);

    public abstract T update(String id, Event<T> event);

    public abstract T delete(String id);

    public abstract T rename(String id, String newId);

    public abstract <R extends RootEntity> RootEntityContext<R> asRootContext();

    public abstract <R extends RootEntity> AsyncEntityContext<R> asAsyncEntityContext();

    public <R extends RootEntity> UnboundedEntityContext<R> asUnboundedEntityContext() {
        throw new UnsupportedOperationException("Not supported.");
    }

    public abstract <R extends RootEntity, K extends ChildEntity<R>> ChildEntityContext<R, K> asChildContext();

    public <R extends RootEntity> ExternalEntityQueryProvider<R> getEntityQueryProvider(Class<R> rootType) {
        return new ExternalEntityQueryProvider(this.queryOperationSelector.apply(rootType), rootType, getTenantId());
    }
    
    public <R extends RootEntity> ExternalEntityQueryProvider<R> getEntityQueryProvider(Class<R> rootType,String tenantId) {
        if(getTenantId() != null && tenantId != null  && !getTenantId().equals(tenantId))
        {
            throw new DomainEventException(DomainEventException.ErrorCode.BASIC_VIOLATION,"cross tenant access from tenant context not allowd");
        }
        return new ExternalEntityQueryProvider(this.queryOperationSelector.apply(rootType), rootType, tenantId);
    }
}
