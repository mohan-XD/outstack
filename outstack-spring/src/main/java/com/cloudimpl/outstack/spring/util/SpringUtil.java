/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.cloudimpl.outstack.spring.util;

import com.cloudimpl.outstack.app.ServiceMeta;
import com.cloudimpl.outstack.core.annon.CloudFunction;
import com.cloudimpl.outstack.core.annon.Router;
import com.cloudimpl.outstack.runtime.CommandHandler;
import com.cloudimpl.outstack.runtime.EntityCommandHandler;
import com.cloudimpl.outstack.runtime.EntityEventHandler;
import com.cloudimpl.outstack.runtime.EntityQueryHandler;
import com.cloudimpl.outstack.runtime.Handler;
import com.cloudimpl.outstack.runtime.ResourceHelper;
import com.cloudimpl.outstack.runtime.ServiceProvider;
import com.cloudimpl.outstack.runtime.common.GsonCodec;
import com.cloudimpl.outstack.runtime.domainspec.Entity;
import com.cloudimpl.outstack.runtime.domainspec.EntityMeta;
import com.cloudimpl.outstack.runtime.domainspec.RootEntity;
import com.cloudimpl.outstack.runtime.util.Util;
import com.cloudimpl.outstack.spring.component.SpringQueryService;
import com.cloudimpl.outstack.spring.component.SpringService;
import com.cloudimpl.outstack.spring.component.SpringServiceDescriptor;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 *
 * @author nuwan
 */
public class SpringUtil {

    public static ServiceMeta serviceProviderMeta(ResourceHelper resourceHelper,Class<? extends SpringService> funcType) {
        CloudFunction func = funcType.getAnnotation(CloudFunction.class);
        Objects.requireNonNull(func);
        Router router = funcType.getAnnotation(Router.class);
        Objects.requireNonNull(func);
        Util.classForName(funcType.getName()); //TODO check class loading issue
        Map<String, String> attr = new HashMap<>();
        attr.put("serviceMeta", GsonCodec.encode(getServiceDescription(resourceHelper.getResourceContext(),func.name(),funcType)));
        //attr.put("plural", value)
        return new ServiceMeta(funcType, func, router, attr);
    }
    
    public static ServiceMeta serviceQueryProviderMeta(ResourceHelper resourceHelper,Class<? extends SpringQueryService> funcType) {
        CloudFunction func = funcType.getAnnotation(CloudFunction.class);
        Objects.requireNonNull(func);
        Router router = funcType.getAnnotation(Router.class);
        Objects.requireNonNull(func);
        Util.classForName(funcType.getName()); //TODO check class loading issue
        Map<String, String> attr = new HashMap<>();
        attr.put("serviceQueryMeta", GsonCodec.encode(getQueryServiceDescription(resourceHelper.getResourceContext(),func.name(),funcType)));
        //attr.put("plural", value)
        return new ServiceMeta(funcType, func, router, attr);
    }

    public static SpringServiceDescriptor getServiceDescription(String appContext,String serviceName,Class<? extends SpringService> serviceType) {
        Class<? extends RootEntity> rootType = Util.extractGenericParameter(serviceType, SpringService.class, 0);
        EntityMeta entityMeta = rootType.getAnnotation(EntityMeta.class);
        Collection<Class<? extends CommandHandler<?>>> handlers = SpringService.handlers(rootType);
        SpringServiceDescriptor desc = new SpringServiceDescriptor(appContext,serviceName, rootType.getSimpleName(), entityMeta.version(), entityMeta.plural(), Entity.hasTenant(rootType));
        handlers.stream().filter(h -> EntityCommandHandler.class.isAssignableFrom(h)).forEach(h -> {
            Class<? extends Entity> eType = Util.extractGenericParameter(h, EntityCommandHandler.class, 0);
            EntityMeta eMeta = eType.getAnnotation(EntityMeta.class);
            SpringServiceDescriptor.EntityDescriptor entityDesc = new SpringServiceDescriptor.EntityDescriptor(eType.getSimpleName(), eMeta.plural());

            if (eType == rootType) {
                desc.putRootAction(new SpringServiceDescriptor.ActionDescriptor(h.getSimpleName(), SpringServiceDescriptor.ActionDescriptor.ActionType.COMMAND_HANDLER));
            } else {
                desc.putChildAction(entityDesc, new SpringServiceDescriptor.ActionDescriptor(h.getSimpleName(), SpringServiceDescriptor.ActionDescriptor.ActionType.COMMAND_HANDLER));
            }
        });

        handlers.stream().filter(h -> EntityEventHandler.class.isAssignableFrom(h)).forEach(h -> {
            System.out.println("load: "+h.getName());
            Class<? extends Entity> eType = Util.extractGenericParameter(h, EntityEventHandler.class, 0);
            EntityMeta eMeta = eType.getAnnotation(EntityMeta.class);
            SpringServiceDescriptor.EntityDescriptor entityDesc = new SpringServiceDescriptor.EntityDescriptor(eType.getSimpleName(), eMeta.plural());
            if (eType == rootType) {
                desc.putRootAction(new SpringServiceDescriptor.ActionDescriptor(h.getSimpleName(), SpringServiceDescriptor.ActionDescriptor.ActionType.EVENT_HANDLER));
            } else {
                desc.putChildAction(entityDesc, new SpringServiceDescriptor.ActionDescriptor(h.getSimpleName(), SpringServiceDescriptor.ActionDescriptor.ActionType.EVENT_HANDLER));
            }
        });

        SpringService.cmdEntities(rootType).forEach(type -> {
            EntityMeta eMeta = type.getAnnotation(EntityMeta.class);
            SpringServiceDescriptor.EntityDescriptor entityDesc = new SpringServiceDescriptor.EntityDescriptor(type.getSimpleName(), eMeta.plural());
            if (type == rootType) {
                desc.putRootAction(new SpringServiceDescriptor.ActionDescriptor("Delete"+type.getSimpleName(), SpringServiceDescriptor.ActionDescriptor.ActionType.COMMAND_HANDLER));
                desc.putRootAction(new SpringServiceDescriptor.ActionDescriptor("Rename"+type.getSimpleName(), SpringServiceDescriptor.ActionDescriptor.ActionType.COMMAND_HANDLER));
            } else {
                desc.putChildAction(entityDesc, new SpringServiceDescriptor.ActionDescriptor("Delete"+type.getSimpleName(), SpringServiceDescriptor.ActionDescriptor.ActionType.COMMAND_HANDLER));
                desc.putChildAction(entityDesc,new SpringServiceDescriptor.ActionDescriptor("Rename"+type.getSimpleName(), SpringServiceDescriptor.ActionDescriptor.ActionType.COMMAND_HANDLER));
            }

        });
        return desc;
    }

    public static SpringServiceDescriptor getQueryServiceDescription(String appContext,String serviceName,Class<? extends SpringQueryService> serviceType) {
        Class<? extends RootEntity> rootType = Util.extractGenericParameter(serviceType, SpringQueryService.class, 0);
        EntityMeta entityMeta = rootType.getAnnotation(EntityMeta.class);
        Collection<Class<? extends Handler<?>>> handlers = SpringQueryService.handlers(rootType);
        SpringServiceDescriptor desc = new SpringServiceDescriptor(appContext,serviceName, rootType.getSimpleName(), entityMeta.version(), entityMeta.plural(), Entity.hasTenant(rootType));
        handlers.stream().filter(h -> EntityQueryHandler.class.isAssignableFrom(h)).forEach(h -> {
            Class<? extends Entity> eType = Util.extractGenericParameter(h, EntityQueryHandler.class, 0);
            EntityMeta eMeta = eType.getAnnotation(EntityMeta.class);
            SpringServiceDescriptor.EntityDescriptor entityDesc = new SpringServiceDescriptor.EntityDescriptor(eType.getSimpleName(), eMeta.plural());
            if (eType == rootType) {
                desc.putRootAction(new SpringServiceDescriptor.ActionDescriptor(h.getSimpleName(), SpringServiceDescriptor.ActionDescriptor.ActionType.QUERY_HANDLER));
            } else {
                desc.putChildAction(entityDesc, new SpringServiceDescriptor.ActionDescriptor(h.getSimpleName(), SpringServiceDescriptor.ActionDescriptor.ActionType.QUERY_HANDLER));
            }
        });
        SpringQueryService.queryEntities(rootType).forEach(type -> {
            EntityMeta eMeta = type.getAnnotation(EntityMeta.class);
            SpringServiceDescriptor.EntityDescriptor entityDesc = new SpringServiceDescriptor.EntityDescriptor(type.getSimpleName(), eMeta.plural());
            if (type == rootType) {
                desc.putRootAction(new SpringServiceDescriptor.ActionDescriptor("Get"+type.getSimpleName(), SpringServiceDescriptor.ActionDescriptor.ActionType.QUERY_HANDLER));
                desc.putRootAction(new SpringServiceDescriptor.ActionDescriptor("List"+type.getSimpleName(), SpringServiceDescriptor.ActionDescriptor.ActionType.QUERY_HANDLER));
            } else {
                desc.putChildAction(entityDesc, new SpringServiceDescriptor.ActionDescriptor("Get"+type.getSimpleName(), SpringServiceDescriptor.ActionDescriptor.ActionType.QUERY_HANDLER));
                desc.putChildAction(entityDesc,new SpringServiceDescriptor.ActionDescriptor("List"+type.getSimpleName(), SpringServiceDescriptor.ActionDescriptor.ActionType.QUERY_HANDLER));
            }

        });
        return desc;
    }
}
