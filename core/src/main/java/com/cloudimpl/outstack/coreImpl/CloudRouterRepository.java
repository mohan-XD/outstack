/*
 * To change this license header, choose License Headers in Project Properties. To change this template file, choose
 * Tools | Templates and open the template in the editor.
 */
package com.cloudimpl.outstack.coreImpl;

import com.cloudimpl.outstack.common.FluxMap;
import com.cloudimpl.outstack.core.CloudRouter;
import com.cloudimpl.outstack.core.CloudRouterDescriptor;
import com.cloudimpl.outstack.core.CloudService;
import com.cloudimpl.outstack.core.CloudUtil;
import com.cloudimpl.outstack.core.Inject;
import com.cloudimpl.outstack.core.Injector;
import com.cloudimpl.outstack.core.RouterException;
import com.cloudimpl.outstack.core.logger.ILogger;
import com.cloudimpl.outstack.logger.Logger;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import reactor.core.publisher.Flux;

/**
 *
 * @author nuwansa
 */
public class CloudRouterRepository {
  private final Map<String, CloudRouter> routers = new ConcurrentHashMap<>();
  private final Map<String, CloudRouterDescriptor> routerTypes = new ConcurrentHashMap<>();
  private final Injector injector;
  private final ILogger logger;

  @Inject
  public CloudRouterRepository(Injector injector, Logger rootLogger) {
    this.injector = injector;
    logger = rootLogger.createSubLogger(CloudRouterRepository.class);
  }

  public CloudRouter router(String topic) {
    return routers.computeIfAbsent(topic,
        name -> CloudUtil.newInstance(
            injector.with("@topic", name).with(CloudRouterDescriptor.class, routerType(name)),
            routerType(topic).getRouterType()));
  }

  private void register(String topic, CloudRouterDescriptor desc) {
    this.routerTypes.put(topic, desc);
    logger.info("router desc {0} register for topic {1}", desc, topic);

  }

  private CloudRouterDescriptor routerType(String topic) {
    CloudRouterDescriptor desc = routerTypes.get(topic);
    if (desc == null)
      throw new RouterException("router not found for topic " + topic);
    return desc;
  }

  protected void subscribe(Flux<FluxMap.Event<String, CloudService>> flux) {
    flux.filter(e -> e.getType() == FluxMap.Event.Type.ADD || e.getType() == FluxMap.Event.Type.UPDATE)
        .subscribe(
            e -> register(e.getValue().name(),
                e.getValue().getDescriptor().getRouterDescriptor()));
  }
}
