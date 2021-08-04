/*
 * Copyright 2021 nuwan.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.cloudimpl.outstack.spring.service.config;

import com.cloudimpl.outstack.runtime.AsyncEntityCommandHandler;
import com.cloudimpl.outstack.runtime.AsyncEntityContext;
import com.cloudimpl.outstack.runtime.EntityContext;
import com.cloudimpl.outstack.runtime.configs.ConfigCreated;
import com.cloudimpl.outstack.runtime.configs.ConfigEntity;
import com.cloudimpl.outstack.runtime.configs.ConfigGroupCreated;
import com.cloudimpl.outstack.runtime.configs.ConfigGroupEntity;
import com.cloudimpl.outstack.runtime.configs.CreateConfigRequest;
import reactor.core.publisher.Mono;

/**
 *
 * @author nuwan
 */
public class CreateConfigGroupEntity extends AsyncEntityCommandHandler<ConfigGroupEntity, CreateConfigRequest,ConfigEntity>{

    @Override
    protected Mono<ConfigEntity> execute(EntityContext<ConfigGroupEntity> context, CreateConfigRequest command) {
        AsyncEntityContext<ConfigGroupEntity> asyncContext = context.asAsyncEntityContext();
        ConfigGroupEntity group = asyncContext.<ConfigGroupEntity>getEntityById(command.getGroupName()).orElseGet(()->asyncContext.<ConfigGroupEntity>create(command.getGroupName(), new ConfigGroupCreated(command.getGroupName())));
        return Mono.just(asyncContext.create(ConfigEntity.class, group.id(), command.getConfigName(), new ConfigCreated(group.entityId(), command.getConfigName(),command.getValue(), command.getConfigType())));
    }
    
}
