/*
 * To change this license header, choose License Headers in Project Properties. To change this template file, choose
 * Tools | Templates and open the template in the editor.
 */
package com.cloudimpl.outstack.node;

import com.cloudimpl.outstack.common.CloudMessage;
import com.cloudimpl.outstack.common.EndpointListener;
import com.cloudimpl.outstack.common.FluxMap;
import com.cloudimpl.outstack.common.GsonCodec;
import com.cloudimpl.outstack.common.RouteEndpoint;
import com.cloudimpl.outstack.common.TransportManager;
import com.cloudimpl.outstack.core.CloudFunction;
import com.cloudimpl.outstack.core.CloudServiceDescriptor;
import com.cloudimpl.outstack.core.CloudUtil;
import com.cloudimpl.outstack.core.Injector;
import com.cloudimpl.outstack.core.ServiceRegistryReadOnly;
import com.cloudimpl.outstack.core.logger.ILogger;
import com.cloudimpl.outstack.coreImpl.CloudEngine;
import com.cloudimpl.outstack.coreImpl.CloudEngineImpl;
import com.cloudimpl.outstack.coreImpl.CloudMsgHdr;
import com.cloudimpl.outstack.coreImpl.RemoteCloudService;
import com.cloudimpl.outstack.logger.Logger;
import com.cloudimpl.outstack.routers.LocalRouter;
import com.fasterxml.jackson.databind.util.ByteBufferBackedInputStream;
import io.scalecube.cluster.Cluster;
import io.scalecube.cluster.ClusterImpl;
import io.scalecube.cluster.ClusterMessageHandler;
import io.scalecube.cluster.membership.MembershipEvent;
import io.scalecube.cluster.metadata.MetadataCodec;
import io.scalecube.cluster.transport.api.Message;
import io.scalecube.cluster.transport.api.MessageCodec;
import io.scalecube.net.Address;
import io.scalecube.transport.netty.tcp.TcpTransportFactory;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 *
 * @author nuwansa
 */
public class CloudNode implements MetadataCodec {

    private final CloudEngine engine;
    private final NodeConfig config;
    private Cluster gossipCluster;
    private final Injector injector;
    private final TransportManager transportManager;
    private final ILogger logger;
    private final String id;
    private final Map<String, String> serviceCache = new ConcurrentHashMap<>();

    public CloudNode(Injector injector, NodeConfig config) {
        this.injector = injector;
        this.id = getNodeId();
        serviceCache.put("node_id", this.id);
        injector.bind("@host").to(CloudUtil.getHostIpAddr());
        injector.bind("@nodeId").to(id);
        this.transportManager = new TransportManager(com.cloudimpl.outstack.common.JsonMessageCodec.instance());
        this.injector.bind(TransportManager.class).to(this.transportManager);
        this.engine = new CloudEngineImpl(() -> getMemberId(), id, injector, config);
        this.config = config;
        this.logger = injector.inject(Logger.class).createSubLogger(CloudNode.class);
    }

    public <T> Mono<T> requestReply(String topic, Object request) {
        return engine.requestReply(topic, request);
    }

    public <T> Flux<T> requestStream(String topic, Object request) {
        return engine.requestStream(topic, request);
    }

    public Mono<Void> send(String topic, Object data) {
        return engine.send(topic, data);
    }

    public void registerService(String name, CloudFunction cloudFunc) {
        engine.registerService(name, cloudFunc);
    }

    public NodeConfig getConfig() {
        return config;
    }

    public ServiceRegistryReadOnly getServiceRegistry()
    {
        return engine.getServiceRegistry();
    }
    
    private String getNodeId() {
        String id = System.getenv("NODE_ID");
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        return id;
    }

    public void start() {

        gossipCluster = new ClusterImpl()
                .membership(options -> options.seedMembers(config.getSeeds()))
                .transportFactory(TcpTransportFactory::new)
                .config(opt -> opt
                .metadataCodec(this)
                .transport(op -> op.port(config.getGossipPort()).messageCodec(new MessageCodecImpl())))
                .handler(
                        cluster -> {
                            gossipCluster = cluster;
                            return new ClusterMessageHandler() {
                        @Override
                        public void onMembershipEvent(MembershipEvent event) {
                            if (event.isUpdated() || event.isAdded()) {
                                onMemberEvent(event);
                            } else if (event.isRemoved()) {
                                engine.getServiceRegistry().unregisterByMemberId(event.member().id());
                            }
                        }
                    };
                        })
                .startAwait();

        publishServices();
        startEndpointServices();
    }

    public void shutdown() {
        this.transportManager.close();
        this.gossipCluster.shutdown();
    }

    private String getMemberId() {
        return gossipCluster.member().id();
    }

    private void startEndpointServices() {
        logger.info("node : {0} endpoint created in host {1}, port {2}", id, CloudUtil.getHostIpAddr(), config.getNodePort());
        transportManager.createEndpoint(CloudUtil.getHostIpAddr(), config.getNodePort(), new EndpointListener<CloudMessage>() {
            @Override
            public Mono<Void> fireAndForget(CloudMessage msg) {
                return engine.getServiceRegistry().findLocal(msg.attr(CloudMsgHdr.SERVICE_ID)).send(msg);
            }

            @Override
            public Mono<CloudMessage> requestResponse(CloudMessage msg) {
                return engine.getServiceRegistry().findLocal(msg.attr(CloudMsgHdr.SERVICE_ID)).requestReply(msg)
                        .map(d -> CloudMessage.builder().withData(d).build());
            }

            @Override
            public Flux<CloudMessage> requestStream(CloudMessage msg) {
                return engine.getServiceRegistry().findLocal(msg.attr(CloudMsgHdr.SERVICE_ID)).requestStream(msg)
                        .map(d -> CloudMessage.builder().withData(d).build());
            }

        });
        startOtherEndpointServices();
    }

    private void startOtherEndpointServices() {
        config.getServiceEndpoints().stream().map(s -> CloudUtil.newInstance(injector, s))
                .peek(s -> logger.info("service endpoint created for {0} host: {1} , port {2}", s.name(), s.getHostAddr(), s.getServicePort()))
                .forEach(e -> transportManager.createEndpoint(e.getHostAddr(), e.getServicePort(), e.getEndpointListener(engine)));
    }

    private void publishServices() {

        // listen to service addition and updates
        engine.getServiceRegistry().localFlux()
                .filter(e -> e.getType() == FluxMap.Event.Type.ADD || e.getType() == FluxMap.Event.Type.UPDATE)
                .map(e -> e.getValue().getDescriptor())
                .doOnNext(desc -> logger.info("local service update : {0}", desc))
                .doOnNext(desc -> serviceCache.put("srv_" + desc.getServiceId(), desc.toString()))
                .doOnNext(
                        desc -> {
                            if (desc.getRouterDescriptor().getRouterType() != LocalRouter.class) {
                                gossipCluster.updateMetadata(serviceCache).subscribe();
                            }
                        })
                .doOnError(err -> logger.exception(err, "error updating membership service"))
                .subscribe();
        // listen to service removal
        engine.getServiceRegistry().localFlux()
                .filter(e -> e.getType() == FluxMap.Event.Type.REMOVE)
                .doOnNext(e -> serviceCache.remove("srv_" + e.getValue().id()))
                .doOnNext(e -> logger.info("local service removed : {0}", e.getValue().getDescriptor()))
                .doOnNext(e -> {
                    if (e.getValue().getDescriptor().getRouterDescriptor().getRouterType() != LocalRouter.class) {
                        gossipCluster.updateMetadata(serviceCache).subscribe();
                    }
                }).subscribe();
    }

    private void onMemberEvent(MembershipEvent event) {

        try {
            logger.info("member event {0} received", event);
            //String nodeId = event.member().id();
            Optional<Map<String, String>> newMetaOptional = gossipCluster.metadata(event.member());
            if (!newMetaOptional.isPresent()) {
                return;
            }
            Map<String, String> newMeta = newMetaOptional.get();
            String nodeId = newMeta.get("node_id");
            if (nodeId == null) {
                logger.error("node id is not found ,ignore event");
                return;
            }
            logger.info("membership received from nodeid {0}", nodeId);
            Set<String> keys = new HashSet<>(newMeta.keySet());

            engine.getServiceRegistry().services().filter(srv -> srv.nodeId().equals(nodeId)).forEach(srv -> {
                if (!newMeta.containsKey("srv_" + srv.id())) {
                    engine.getServiceRegistry().unregister(srv.id());
                    logger.info("remote service removed : {0}", srv.getDescriptor());
                } else {
                    keys.remove(srv.id());
                }
            });

            // add new services;
            keys.stream().filter(k -> k.startsWith("srv_")).map(k -> newMeta.get(k))
                    .map(e -> GsonCodec.decode(CloudServiceDescriptor.class, e))
                    .forEach(desc -> this.updateRegistry(event.member().id(), nodeId, desc));
        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }

    private void updateRegistry(String memberId, String nodeId, CloudServiceDescriptor desc) {

        logger.info("remote service registered. {0}", desc);
        Address address = Address.create(desc.getHostAddr(), desc.getServicePort());
        RemoteCloudService remoteService = new RemoteCloudService(nodeId, memberId,
                () -> transportManager.get(RouteEndpoint.create(address.host(), address.port())), desc);
        engine.getServiceRegistry().register(remoteService);

    }

    private Object decode(ByteBuffer byteBuffer) {
        try {
            return DefaultObjectMapper.OBJECT_MAPPER.readValue(
                    new ByteBufferBackedInputStream(byteBuffer), Map.class);
            // return GsonCodec.decode(Map.class, new String(byteBuffer.array()));
        } catch (Exception e) {
            logger.exception(e, "Failed to read metadata: ");
            return null;
        }
    }

    private ByteBuffer encode(Object input) {
        Map<String, String> serviceEndpoint = (Map<String, String>) input;
        try {
            return ByteBuffer.wrap(
                    DefaultObjectMapper.OBJECT_MAPPER
                            .writeValueAsString(serviceEndpoint)
                            .getBytes(StandardCharsets.UTF_8));
            // return ByteBuffer.wrap(GsonCodec.encode(serviceEndpoint).getBytes());
        } catch (Exception e) {
            logger.exception(e, "Failed to write metadata: ");
            throw Exceptions.propagate(e);
        }
    }

    @Override
    public Object deserialize(ByteBuffer bb) {
        try {
            return DefaultObjectMapper.OBJECT_MAPPER.readValue(
                    new ByteBufferBackedInputStream(bb), Map.class);
            // return GsonCodec.decode(Map.class, new String(byteBuffer.array()));
        } catch (Exception e) {
            // logger.exception(e, "Failed to read metadata: ");
            return null;
        }
    }

    @Override
    public ByteBuffer serialize(Object input) {
        Map<String, String> serviceEndpoint = (Map<String, String>) input;
        try {
            return ByteBuffer.wrap(
                    DefaultObjectMapper.OBJECT_MAPPER
                            .writeValueAsString(serviceEndpoint)
                            .getBytes(StandardCharsets.UTF_8));
            // return ByteBuffer.wrap(GsonCodec.encode(serviceEndpoint).getBytes());
        } catch (Exception e) {
            // logger.exception(e, "Failed to write metadata: ");
            throw Exceptions.propagate(e);
        }
    }

    private static class MessageCodecImpl implements MessageCodec {

        @Override
        public Message deserialize(InputStream stream) throws Exception {
            return DefaultObjectMapper.OBJECT_MAPPER.readValue(stream, Message.class);
        }

        @Override
        public void serialize(Message message, OutputStream stream) throws Exception {
            DefaultObjectMapper.OBJECT_MAPPER.writeValue(stream, message);
        }
    }

}
