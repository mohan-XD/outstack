/*
 * Copyright 2020 nuwansa.
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
package com.cloudimpl.outstack.common;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

/**
 *
 * @author nuwansa
 * @param <T>
 */
public class FluxProcessor<T> {
    private final List<FluxSink<T>> list;
    private final Flux<T> flux;
    public FluxProcessor() {
       this(t->{});
    }
    
    public FluxProcessor(Consumer<FluxSink<T>> consumer) {
        list = new CopyOnWriteArrayList<>();
        flux = Flux.<T>create(emitter->{
            System.out.println("subscription added:"+Thread.currentThread().getName());
            consumer.accept(emitter);
            list.add(emitter);
            emitter.onCancel(()->this.remove(emitter));
            emitter.onDispose(()->this.remove(emitter));
        });
    }
    
    public void add(T t)
    {
        list.forEach(sink->sink.next(t));
    }
    
    public Flux<T> flux()
    {
        return flux;
    }
    
    
    private void remove(FluxSink sink)
    {
        System.out.println("remove from :"+Thread.currentThread().getName());
        list.remove(sink);
    }
}
