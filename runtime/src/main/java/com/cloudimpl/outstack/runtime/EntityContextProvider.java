/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.cloudimpl.outstack.runtime;

import static com.cloudimpl.outstack.runtime.EventRepositoy.TID_PREFIX;

import com.cloudimpl.outstack.runtime.domainspec.*;
import com.cloudimpl.outstack.runtime.util.Util;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.validation.ConstraintViolation;
import javax.validation.Validator;

import reactor.core.publisher.Mono;

/**
 *
 * @author nuwan
 * @param <T>
 */
public class EntityContextProvider<T extends RootEntity> extends EntityQueryContextProvider<T> {

    private final EntityProvider entityProvider;
    private final Supplier<BiFunction<String, Object, Mono>> requestHandler;

    public EntityContextProvider(Class<T> type, EntityProvider entityProvider, Supplier<String> idGenerator,
            QueryOperations<T> queryOperation, Function<Class<? extends RootEntity>, QueryOperations<?>> queryOperationSelector, Supplier<BiFunction<String, Object, Mono>> requestHandler) {
        super(type, idGenerator, queryOperation, queryOperationSelector, requestHandler);
        this.entityProvider = entityProvider;
        this.requestHandler = requestHandler;
    }

    public Transaction<T> createWritableTransaction(String rootTid, String tenantId, boolean async) {
        return new Transaction(type, entityProvider, idGenerator, rootTid, tenantId, queryOperation, this::validateObject, this.queryOperationSelector, version, async, requestHandler);
    }

    public UnboundedTransaction<T> createUnboundedTransaction(String tenantId) {
        return new UnboundedTransaction<T>(this, tenantId, idGenerator, queryOperation, this::validateObject, this.queryOperationSelector, type, requestHandler);
    }

    private <T> void validateObject(T target) {
        Set<ConstraintViolation<T>> violations = this.validator.validate(target);
        if (!violations.isEmpty()) {
            ValidationErrorException error = new ValidationErrorException(violations.stream().findFirst().get().getMessage());
            throw error;
        }
    }

    public static final class Transaction< R extends RootEntity> extends ReadOnlyTransaction<R> implements CRUDOperations {

        private final TreeMap<String, Entity> mapBrnEntities;
        private final TreeMap<String, Entity> mapTrnEntities;
        private final Map<String, Entity> removeEntites;
        private final Map<String, Entity> renameEntities;
        private final EntityProvider entityProvider;
        private Object reply;
        private final List<Event> eventList;
        private final Supplier<BiFunction<String, Object, Mono>> requestHandler;
        private Object attachment;
        private InputMetaProvider inputMetaProvider;
        
        public Transaction(Class<R> type, EntityProvider entityProvider, Supplier<String> idGenerator, String rootId,
                String tenantId, QueryOperations<R> queryOperation, Consumer<Object> validator, Function<Class<? extends RootEntity>, QueryOperations<?>> queryOperationSelector, String version, boolean async, Supplier<BiFunction<String, Object, Mono>> requestHandler) {
            super(type, idGenerator, rootId, tenantId, queryOperation, validator, queryOperationSelector, version, async, requestHandler);
            this.mapBrnEntities = new TreeMap<>();
            this.mapTrnEntities = new TreeMap<>();
            this.entityProvider = entityProvider;
            this.eventList = new LinkedList<>();
            this.requestHandler = requestHandler;
            this.removeEntites = new HashMap<>();
            this.renameEntities = new HashMap<>();
        }

        @Override
        public void setReply(Object reply) {
            this.reply = reply;
        }

        @Override
        public <K> K getReply() {
            return (K) reply;
        }

        @Override
        public List<Event> getEventList() {
            return eventList;
        }

        protected void publishEvent(Event event) {
            this.eventList.add(event);
        }

        public InputMetaProvider getInputMetaProvider() {
            return inputMetaProvider;
        }

        protected Transaction setInputMetaProvider(InputMetaProvider inputMetaProvider) {
            this.inputMetaProvider = inputMetaProvider;
            return this;
        }
        @Override
        public <C extends ChildEntity<R>, K extends Entity, Z extends EntityQueryContext> Z getContext(Class<K> entityType) {
            if (RootEntity.isMyType(entityType)) {
                Class<R> rootType = (Class<R>) entityType;
                if (async) {
                    return (Z) new AsyncEntityContext(rootType,
                            rootTid, tenantId,
                            Optional.of((EntityProvider) this::loadEntity),
                            idGenerator, Optional.of((CRUDOperations) this),
                            this,
                            Optional.of((Consumer<Event>) this::publishEvent),
                            validator, this.queryOperationSelector, version, requestHandler.get()).setTx(this);
                } else {
                    return (Z) new RootEntityContext(rootType,
                            rootTid, tenantId,
                            Optional.of((EntityProvider) this::loadEntity),
                            idGenerator, Optional.of((CRUDOperations) this),
                            this,
                            Optional.of((Consumer<Event>) this::publishEvent),
                            validator, this.queryOperationSelector, version).setTx(this);
                }

            } else {
                validateRootTid();
                Class<R> rootType = Util.extractGenericParameter(entityType, ChildEntity.class, 0);
                Class<C> childType = (Class<C>) entityType;
                return (Z) new ChildEntityContext<>(
                        rootType,
                        rootTid, childType, tenantId,
                        Optional.of((EntityProvider) this::loadEntity), idGenerator, Optional.of((CRUDOperations) this),
                        this, Optional.of((Consumer<Event>) this::publishEvent), validator, this.queryOperationSelector, version).setTx(this);
            }
        }

        public <K extends Entity, C extends ChildEntity<R>> Optional<K> loadEntity(Class<R> rootType, String id, Class<C> childType, String childId, String tenantId) {
            if (childType == null) {
                return (Optional<K>) loadRootEntity(rootType, id, tenantId)
                        .or(() -> entityProvider.loadEntity(rootType, id, childType, childId, tenantId));
            } else {
                return (Optional<K>) loadChildEntity(rootType, id, childType, childId, tenantId).or(() -> entityProvider.loadEntity(rootType, id, childType, childId, tenantId));
            }
        }

        protected Optional<R> loadRootEntity(Class<R> rootType, String id, String tenantId) {
            if (id.startsWith(TID_PREFIX)) {
                return Optional.ofNullable((R) mapTrnEntities.get(RootEntity.makeTRN(rootType, version, id, tenantId)));
            } else {
                return Optional.ofNullable((R) mapBrnEntities.get(RootEntity.makeRN(rootType, version, id, tenantId)));
            }
        }

        protected <C extends ChildEntity<R>> Optional<C> loadChildEntity(Class<R> rootType, String id, Class<C> childType, String childId, String tenantId) {
            EntityIdHelper.validateTechnicalId(id);
            if (childId.startsWith(TID_PREFIX)) {
                return Optional.ofNullable((C) mapTrnEntities.get(ChildEntity.makeTRN(rootType, version, id, childType, childId, tenantId)));
            } else {
                return Optional.ofNullable((C) mapBrnEntities.get(ChildEntity.makeRN(rootType, version, id, childType, childId, tenantId)));
            }
        }

        public void putEntity(Entity entity) {
            mapBrnEntities.put(entity.getBRN(), entity);
            mapTrnEntities.put(entity.getTRN(), entity);
            if (entity.isRoot()) {
                if (this.rootTid == null) {
                    this.rootTid = entity.id();
                } else if (!this.rootTid.equals(entity.id())) {
                    throw new ServiceProviderException("multiple root id modification on same transaction not supported. expecting {0} , found {1}", this.rootTid, entity.entityId());
                }
            }
        }

        @Override
        public void create(Entity entity) {
            putEntity(entity);
        }

        @Override
        public void update(Entity entity) {
            putEntity(entity);
        }

        @Override
        public void delete(Entity entity) {
            mapBrnEntities.remove(entity.getBRN());
            removeEntites.put(entity.getBRN(), entity);
        }

        @Override
        public void rename(Entity oldEntity, Entity newEntity) {
            renameEntities.put(oldEntity.getTRN(), oldEntity);
            mapBrnEntities.remove(oldEntity.getBRN());
            removeEntites.put(oldEntity.getBRN(), oldEntity);
            mapBrnEntities.put(newEntity.getBRN(), newEntity);
            mapTrnEntities.put(newEntity.getTRN(), newEntity);
        }

        @Override
        public Optional<R> getRootById(Class<R> rootType, String id, String tenantId) {
            if (id.startsWith(TID_PREFIX)) {
                return Optional.ofNullable((R) mapTrnEntities.get(RootEntity.makeTRN(rootType, version, id, tenantId))).or(() -> queryOperation.getRootById(rootType, id, tenantId));
            } else {
                return Optional.ofNullable((R) mapBrnEntities.get(RootEntity.makeRN(rootType, version, id, tenantId))).or(() -> queryOperation.getRootById(rootType, id, tenantId));
            }
        }

        @Override
        public <T extends ChildEntity<R>> Optional<T> getChildById(Class<R> rootType, String id, Class<T> childType, String childId, String tenantId) {
            EntityIdHelper.validateTechnicalId(id);
            if (childId.startsWith(TID_PREFIX)) {
                return Optional.ofNullable((T) mapTrnEntities.get(ChildEntity.makeTRN(rootType, version, id, childType, id, tenantId)))
                        .or(() -> queryOperation.getChildById(rootType, id, childType, childId, tenantId));
            } else {
                return Optional.ofNullable((T) mapBrnEntities.get(ChildEntity.makeRN(rootType, version, id, childType, childId, tenantId)))
                        .or(() -> queryOperation.getChildById(rootType, id, childType, childId, tenantId));
            }

        }

        @Override
        public <T extends ChildEntity<R>> ResultSet<T> getAllChildByType(Class<R> rootType, String id, Class<T> childType, String tenantId, Query.PagingRequest pageable) {

            Map<String, T> map = (Map<String, T>) new HashMap<>(queryOperation.getAllChildByType(rootType, id, childType, tenantId, pageable).getItems().stream().collect(Collectors.toMap(c -> c.getTRN(), c -> (T) c)));
            mapTrnEntities.headMap(RootEntity.makeTRN(rootType, version, id, tenantId)).entrySet().forEach(p -> map.put(p.getKey(), (T) p.getValue()));
            Collection<T> out = map.values();
            return new ResultSet<>(out.size(), (int) Math.ceil(((double) out.size()) / pageable.pageSize()), pageable.pageNum(), out);
        }

        @Override
        public ResultSet<R> getAllByRootType(Class<R> rootType, String tenantId, Query.PagingRequest paging) {

            return queryOperation.getAllByRootType(rootType, tenantId, paging);
        }

        @Override
        public void setAttachment(Object attachment) {
            this.attachment = attachment;
        }

        @Override
        public <K> K getAttachment() {
            return (K) attachment;
        }

        @Override
        public Collection<Entity> getEntityList() {
            return mapBrnEntities.values();
        }

        @Override
        public Map<String, Entity> getDeletedEntities() {
            return removeEntites;
        }

        @Override
        public Map<String, Entity> getRenameEntities() {
            return renameEntities;
        }
    }

    public static final class UnboundedTransaction< R extends RootEntity> implements ITransaction<R>, CRUDOperations {
        private EntityContextProvider entityContextProvider;
        private String tenantId;
        private Supplier<String> idGenerator;
        private QueryOperations<R> queryOperation;
        private Consumer<Object> validator;
        private Function<Class<? extends RootEntity>, QueryOperations<?>> queryOperationSelector;
        private Map<String, ITransaction<R>> transactionMap = new ConcurrentHashMap<>();
        private EntityMetaDetail entityMetaDetail;
        private InputMetaProvider inputMetaProvider;
        private Object reply;
        private Supplier<BiFunction<String, Object, Mono>> requestHandler;

        public UnboundedTransaction(EntityContextProvider entityContextProvider, String tenantId, Supplier<String> idGenerator, QueryOperations<R> queryOperation,
                                    Consumer<Object> validator, Function<Class<? extends RootEntity>, QueryOperations<?>> queryOperationSelector,
                                    Class<? extends Entity> entityType, Supplier<BiFunction<String, Object, Mono>> requestHandler) {
            this.entityContextProvider = entityContextProvider;
            this.tenantId = tenantId;
            this.idGenerator = idGenerator;
            this.queryOperation = queryOperation;
            this.validator = validator;
            this.queryOperationSelector = queryOperationSelector;
            this.entityMetaDetail = EntityMetaDetailCache.instance().getEntityMeta(entityType);
            this.requestHandler = requestHandler;
        }

        protected void setInputMetaProvider(InputMetaProvider inputMetaProvider) {
            this.inputMetaProvider = inputMetaProvider;
        }

        public void setReply(Object reply) {
            this.reply = reply;
        }

        @Override
        public List<Event> getEventList() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Collection<Entity> getEntityList() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Map<String, Entity> getDeletedEntities() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Map<String, Entity> getRenameEntities() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void setAttachment(Object attachment) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public <K> K getAttachment() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public boolean isEntityRenamed(String trn) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Collection<ITransaction<R>> getTxList() {
            return transactionMap.values();
        }

        @Override
        public <K> K getReply() {
            return (K) reply;
        }

        @Override
        public <C extends ChildEntity<R>, K extends Entity, Z extends EntityQueryContext> Z getContext(Class<K> entityType) {
            if (RootEntity.isMyType(entityType)) {
                Class<R> rootType = (Class<R>) entityType;
                return (Z) new UnboundedEntityContext<>(entityContextProvider, rootType,
                            tenantId,
                            idGenerator, Optional.of((CRUDOperations) this),
                            this.queryOperation,
                            Optional.of((Consumer<Event>) this::publishEvent),
                            validator, this.queryOperationSelector, entityMetaDetail.getVersion(), requestHandler.get());

            } else {
                throw new UnsupportedOperationException("Not supported yet.");
            }
        }

        @Override
        public InputMetaProvider getInputMetaProvider() {
            return inputMetaProvider;
        }

        public <K extends Entity, C extends ChildEntity<R>> Optional<K> loadEntity(Class<R> rootType, String id, Class<C> childType, String childId, String tenantId) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        protected void publishEvent(Event event) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public ResultSet<R> getAllByRootType(Class<R> rootType, String tenantId, Query.PagingRequest paging) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Optional<R> getRootById(Class<R> rootType, String id, String tenantId) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public <T extends ChildEntity<R>> Optional<T> getChildById(Class<R> rootType, String id, Class<T> childType, String childId, String tenantId) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public <T extends ChildEntity<R>> ResultSet<T> getAllChildByType(Class<R> rootType, String id, Class<T> childType, String tenantId, Query.PagingRequest paging) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public ResultSet<Event<R>> getEventsByRootId(Class<R> rootType, String rootId, String tenantId, Query.PagingRequest paging) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public <T extends ChildEntity<R>> ResultSet<Event<T>> getEventsByChildId(Class<R> rootType, String id, Class<T> childType, String childId, String tenantId, Query.PagingRequest paging) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        protected ITransaction<R> getTransaction(String rootId) {
            if(EntityIdHelper.isTechnicalId(rootId)) {
                rootId = queryOperation.getRootById((Class<R>) entityMetaDetail.getType(), rootId,
                        tenantId).orElseThrow(() -> new DomainEventException(DomainEventException.ErrorCode.ENTITY_NOT_FOUND, "root entity not available for entity {0}", entityMetaDetail.getType().getSimpleName())).entityId();
            }
            String tmpRootId = rootId;
            return transactionMap.computeIfAbsent(rootId+":"+tenantId, id -> entityContextProvider.createWritableTransaction(tmpRootId, tenantId, false).setInputMetaProvider(inputMetaProvider));
        }

        protected ITransaction<R> getNonTenantTransaction(String rootId) {
            if (rootId != null) {
                if (EntityIdHelper.isTechnicalId(rootId)) {
                    rootId = queryOperation.getRootById((Class<R>) entityMetaDetail.getType(), rootId,
                            null).orElseThrow(() -> new DomainEventException(DomainEventException.ErrorCode.ENTITY_NOT_FOUND, "root entity not available for entity {0}", entityMetaDetail.getType().getSimpleName())).entityId();
                }
            }
            String tmpRootId = rootId;

            return transactionMap.computeIfAbsent(rootId+":"+null, id -> entityContextProvider.createWritableTransaction(tmpRootId, null, false).setInputMetaProvider(inputMetaProvider));
        }

        @Override
        public void create(Entity entity) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void update(Entity entity) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void delete(Entity entity) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void rename(Entity oldEntity, Entity newEntity) {
            throw new UnsupportedOperationException("Not supported yet.");
        }
    }
}
