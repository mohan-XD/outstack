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
package com.cloudimpl.outstack.core;

import com.cloudimpl.outstack.common.FluxMap;
import com.cloudimpl.outstack.coreImpl.LocalCloudService;
import java.util.Optional;
import java.util.stream.Stream;
import reactor.core.publisher.Flux;

/**
 *
 * @author nuwan
 */
public interface ServiceRegistryReadOnly {
    Flux<FluxMap.Event<String, CloudService>> flux();
    Flux<FluxMap.Event<String, LocalCloudService>> localFlux();
    Stream<CloudService> services();
    Optional<CloudService>findLocalByName(String name);
    CloudService findLocal(String id);
    CloudService findService(String id);
    boolean isServiceExist(String id);
}
