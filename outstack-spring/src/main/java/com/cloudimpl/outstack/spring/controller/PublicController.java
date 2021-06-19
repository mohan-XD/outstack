package com.cloudimpl.outstack.spring.controller;

import com.cloudimpl.outstack.runtime.CommandWrapper;
import com.cloudimpl.outstack.runtime.QueryWrapper;
import com.cloudimpl.outstack.runtime.domainspec.FileData;
import com.cloudimpl.outstack.spring.component.Cluster;
import com.cloudimpl.outstack.spring.component.SpringServiceDescriptor;
import com.cloudimpl.outstack.spring.controller.exception.BadRequestException;
import com.cloudimpl.outstack.spring.controller.exception.NotImplementedException;
import com.cloudimpl.outstack.spring.controller.exception.ResourceNotFoundException;
import com.cloudimpl.outstack.spring.util.FileUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE;

/**
 * Provides public accessible endpoints
 *
 * @author roshanmadhushanka
 **/
@RestController
@CrossOrigin(origins = "*", allowedHeaders = "*",methods = {RequestMethod.DELETE,RequestMethod.GET,RequestMethod.POST})
@RequestMapping("/public")
public class PublicController extends AbstractController {

    public PublicController(Cluster cluster) {
        super(cluster);
    }

    @PostMapping(value = "{context}/{version}/{rootEntity}", consumes = {APPLICATION_JSON_VALUE})
    @SuppressWarnings("unused")
    @ResponseStatus(HttpStatus.CREATED)
    private Mono<ResponseEntity<Object>> createRootEntity(@PathVariable String context, @PathVariable String version,
                                                          @PathVariable String rootEntity,
                                                          @RequestHeader("Content-Type") String contentType,
                                                          @RequestHeader(name = "X-TenantId", required = false) String tenantId,
                                                          @RequestBody String body) {

        SpringServiceDescriptor serviceDesc = getServiceCmdDescriptor(context, version, rootEntity);
        String rootType = serviceDesc.getRootType();
        String cmd = DomainModelDecoder.decode(contentType).orElseGet(() -> "Create" + rootType);
        SpringServiceDescriptor.ActionDescriptor action = serviceDesc.getRootAction(cmd).orElseThrow(() -> new NotImplementedException("resource  {0} creation not implemented", rootType));
        validateAction(action, SpringServiceDescriptor.ActionDescriptor.ActionType.COMMAND_HANDLER);
        CommandWrapper request = CommandWrapper.builder()
                .withCommand(action.getName()).withPayload(body)
                .withVersion(version)
                .withTenantId(tenantId).build();

        return cluster.requestReply(serviceDesc.getServiceName(), request)
                .onErrorMap(this::onError)
                .map(e -> onRootEntityCreation(context, version, rootEntity, e));
    }

    @PostMapping(value = "{context}/{version}/{rootEntity}/{rootId}", consumes = {APPLICATION_JSON_VALUE})
    @SuppressWarnings("unused")
    private Mono<Object> updateRootEntity(@PathVariable String context, @PathVariable String version,
                                          @PathVariable String rootEntity, @PathVariable String rootId,
                                          @RequestHeader("Content-Type") String contentType,
                                          @RequestHeader(name = "X-TenantId", required = false) String tenantId,
                                          @RequestBody String body) {
        SpringServiceDescriptor serviceDesc = getServiceCmdDescriptor(context, version, rootEntity);
        String rootType = serviceDesc.getRootType();
        String cmd = DomainModelDecoder.decode(contentType).orElseGet(() -> "Update" + rootType);
        SpringServiceDescriptor.ActionDescriptor action = serviceDesc.getRootAction(cmd)
                .orElseThrow(() -> new NotImplementedException("resource  {0} update not implemented", rootType));
        validateAction(action, SpringServiceDescriptor.ActionDescriptor.ActionType.COMMAND_HANDLER);
        CommandWrapper request = CommandWrapper.builder()
                .withCommand(action.getName())
                .withVersion(version)
                .withPayload(body)
                .withId(rootId)
                .withRootId(rootId).withTenantId(tenantId).build();

        return cluster.requestReply(serviceDesc.getServiceName(), request)
                .onErrorMap(this::onError);
    }

    @PostMapping(value = "{context}/{version}/{rootEntity}/{rootId}/files", consumes = {MULTIPART_FORM_DATA_VALUE})
    @SuppressWarnings("unused")
    @ResponseStatus(HttpStatus.OK)
    private Mono<Object> uploadRootEntityFiles(@PathVariable String context,
                                               @PathVariable String version,
                                               @PathVariable String rootEntity,
                                               @PathVariable String rootId,
                                               @RequestHeader("Content-Type") String contentType,
                                               @RequestHeader(name = "X-TenantId", required = false) String tenantId,
                                               @RequestPart("files") List<FilePart> files) {

        SpringServiceDescriptor serviceDesc = getServiceCmdDescriptor(context, version, rootEntity);
        String rootType = serviceDesc.getRootType();
        String cmd = DomainModelDecoder.decode(contentType)
                .orElseThrow(() -> new BadRequestException("domain model is not defined"));

        if (CollectionUtils.isEmpty(files)) {
            throw new BadRequestException("no files were attached to the request");
        }

        SpringServiceDescriptor.ActionDescriptor action = serviceDesc.getRootAction(cmd)
                .filter(SpringServiceDescriptor.ActionDescriptor::isFileUploadEnabled)
                .orElseThrow(() -> new NotImplementedException("resource {0} file upload not implemented", rootType));

        List<FileData> fileDataList = files.stream()
                .map(FileUtil::getFileData)
                .collect(Collectors.toList());
        FileUtil.validateMimeType(fileDataList, action.getMimeTypes());

        validateAction(action, SpringServiceDescriptor.ActionDescriptor.ActionType.COMMAND_HANDLER);
        CommandWrapper request = CommandWrapper.builder()
                .withCommand(action.getName())
                .withVersion(version)
                .withFiles(fileDataList.stream().map(e -> (Object) e).collect(Collectors.toList()))
                .withId(rootId)
                .withRootId(rootId).withTenantId(tenantId).build();

        return cluster.requestReply(serviceDesc.getServiceName(), request)
                .onErrorMap(this::onError);
    }

    @PostMapping(value = "{context}/{version}/{rootEntity}/{rootId}/{childEntity}", consumes = {APPLICATION_JSON_VALUE})
    @SuppressWarnings("unused")
    @ResponseStatus(HttpStatus.CREATED)
    private Mono<ResponseEntity<Object>> createChildEntity(@PathVariable String context, @PathVariable String version,
                                                           @PathVariable String rootEntity, @PathVariable String rootId,
                                                           @PathVariable String childEntity,
                                                           @RequestHeader("Content-Type") String contentType,
                                                           @RequestHeader(name = "X-TenantId", required = false) String tenantId,
                                                           @RequestBody String body) {
        SpringServiceDescriptor serviceDesc = getServiceCmdDescriptor(context, version, rootEntity);
        SpringServiceDescriptor.EntityDescriptor child = serviceDesc.getEntityDescriptorByPlural(childEntity)
                .orElseThrow(() -> new ResourceNotFoundException("resource {0}/{1}/{2} not found",
                        rootEntity, rootId, childEntity));
        String cmd = DomainModelDecoder.decode(contentType).orElseGet(() -> "Create" + child.getName());
        SpringServiceDescriptor.ActionDescriptor action = serviceDesc.getChildAction(child.getName(), cmd)
                .orElseThrow(() -> new NotImplementedException("resource  {0} creation not implemented", child.getName()));
        validateAction(action, SpringServiceDescriptor.ActionDescriptor.ActionType.COMMAND_HANDLER);
        CommandWrapper request = CommandWrapper.builder()
                .withCommand(action.getName())
                .withVersion(version)
                .withPayload(body)
                .withRootId(rootId)
                .withTenantId(tenantId).build();

        return cluster.requestReply(serviceDesc.getServiceName(), request)
                .onErrorMap(this::onError)
                .map(r -> this.onChildEntityCreation(context, version, rootEntity, rootId, childEntity, r));
    }

    @PostMapping(value = "{context}/{version}/{rootEntity}/{rootId}/{childEntity}/{childId}", consumes = {APPLICATION_JSON_VALUE})
    @SuppressWarnings("unused")
    private Mono<Object> updateChildEntity(@PathVariable String context, @PathVariable String version, @PathVariable String rootEntity, @PathVariable String rootId, @PathVariable String childEntity, @PathVariable String childId, @RequestHeader("Content-Type") String contentType, @RequestHeader(name = "X-TenantId", required = false) String tenantId, @RequestBody String body) {
        SpringServiceDescriptor serviceDesc = getServiceCmdDescriptor(context, version, rootEntity);
        SpringServiceDescriptor.EntityDescriptor child = serviceDesc.getEntityDescriptorByPlural(childEntity)
                .orElseThrow(() -> new ResourceNotFoundException("resource {0}/{1}/{2} not found",
                        rootEntity, rootId, childEntity));
        String cmd = DomainModelDecoder.decode(contentType).orElseGet(() -> "Update" + child.getName());
        SpringServiceDescriptor.ActionDescriptor action = serviceDesc.getChildAction(child.getName(), cmd)
                .orElseThrow(() -> new NotImplementedException("resource  {0} update not implemented", child.getName()));
        validateAction(action, SpringServiceDescriptor.ActionDescriptor.ActionType.COMMAND_HANDLER);
        CommandWrapper request = CommandWrapper.builder()
                .withCommand(action.getName())
                .withVersion(version)
                .withPayload(body)
                .withId(childId).withRootId(rootId).withTenantId(tenantId).build();

        return cluster.requestReply(serviceDesc.getServiceName(), request)
                .onErrorMap(this::onError);
    }

    @PostMapping(value = "{context}/{version}/{rootEntity}/{rootId}/{childEntity}/{childId}/files",
            consumes = {MULTIPART_FORM_DATA_VALUE})
    @SuppressWarnings("unused")
    @ResponseStatus(HttpStatus.OK)
    private Mono<ResponseEntity<Object>> uploadChildEntityFiles(@PathVariable String context,
                                                                @PathVariable String version,
                                                                @PathVariable String rootEntity,
                                                                @PathVariable String rootId,
                                                                @PathVariable String childEntity,
                                                                @PathVariable String childId,
                                                                @RequestHeader("Content-Type") String contentType,
                                                                @RequestHeader(name = "X-TenantId", required = false) String tenantId,
                                                                @RequestPart("files") List<FilePart> files) {

        SpringServiceDescriptor serviceDesc = getServiceCmdDescriptor(context, version, rootEntity);
        SpringServiceDescriptor.EntityDescriptor child = serviceDesc.getEntityDescriptorByPlural(childEntity)
                .orElseThrow(() -> new ResourceNotFoundException("resource {0}/{1}/{2} not found",
                        rootEntity, rootId, childEntity));
        String cmd = DomainModelDecoder.decode(contentType)
                .orElseThrow(() -> new BadRequestException("domain model is not defined"));

        if (CollectionUtils.isEmpty(files)) {
            throw new BadRequestException("no files were attached to the request");
        }

        SpringServiceDescriptor.ActionDescriptor action = serviceDesc.getChildAction(child.getName(), cmd)
                .filter(SpringServiceDescriptor.ActionDescriptor::isFileUploadEnabled)
                .orElseThrow(() -> new NotImplementedException("resource {0} file upload not implemented",
                        child.getName()));

        List<FileData> fileDataList = files.stream()
                .map(FileUtil::getFileData)
                .collect(Collectors.toList());
        FileUtil.validateMimeType(fileDataList, action.getMimeTypes());

        validateAction(action, SpringServiceDescriptor.ActionDescriptor.ActionType.COMMAND_HANDLER);
        CommandWrapper request = CommandWrapper.builder()
                .withCommand(action.getName())
                .withVersion(version)
                .withFiles(fileDataList.stream().map(e -> (Object) e).collect(Collectors.toList()))
                .withId(childId)
                .withRootId(rootId)
                .withTenantId(tenantId)
                .build();

        return cluster.requestReply(serviceDesc.getServiceName(), request)
                .onErrorMap(this::onError)
                .map(r -> this.onChildEntityCreation(context, version, rootEntity, rootId, childEntity, r));
    }

    @GetMapping(value = "{context}/{version}/{rootEntity}/{rootId}", consumes = {APPLICATION_JSON_VALUE})
    @SuppressWarnings("unused")
    private Mono<Object> getRootEntity(@PathVariable String context, @PathVariable String version,
                                       @PathVariable String rootEntity, @PathVariable String rootId,
                                       @RequestHeader("Content-Type") String contentType,
                                       @RequestHeader(name = "X-TenantId", required = false) String tenantId) {
        SpringServiceDescriptor serviceDesc = getServiceQueryDescriptor(context, version, rootEntity);
        String rootType = serviceDesc.getRootType();
        String query = DomainModelDecoder.decode(contentType).orElseGet(() -> "Get" + rootType);
        SpringServiceDescriptor.ActionDescriptor action = serviceDesc.getRootAction(query)
                .orElseThrow(() -> new NotImplementedException("resource  {0} get not implemented", rootType));
        validateAction(action, SpringServiceDescriptor.ActionDescriptor.ActionType.QUERY_HANDLER);
        QueryWrapper request = QueryWrapper.builder()
                .withVersion(version)
                .withQuery(action.getName())
                .withId(rootId).withRootId(rootId).withTenantId(tenantId).build();

        return cluster.requestReply(serviceDesc.getServiceName(), request)
                .onErrorMap(this::onError);
    }

    @GetMapping(value = "{context}/{version}/{rootEntity}/{rootId}/{childEntity}/{childId}", consumes = {APPLICATION_JSON_VALUE})
    @SuppressWarnings("unused")
    private Mono<Object> getChildEntity(@PathVariable String context, @PathVariable String version,
                                        @PathVariable String rootEntity, @PathVariable String rootId,
                                        @PathVariable String childEntity, @PathVariable String childId,
                                        @RequestHeader("Content-Type") String contentType,
                                        @RequestHeader(name = "X-TenantId", required = false) String tenantId) {
        SpringServiceDescriptor serviceDesc = getServiceQueryDescriptor(context, version, rootEntity);
        SpringServiceDescriptor.EntityDescriptor child = serviceDesc.getEntityDescriptorByPlural(childEntity)
                .orElseThrow(() -> new ResourceNotFoundException("resource {0}/{1}/{2} not found",
                        rootEntity, rootId, childEntity));
        String cmd = DomainModelDecoder.decode(contentType).orElseGet(() -> "Get" + child.getName());
        SpringServiceDescriptor.ActionDescriptor action = serviceDesc.getChildAction(child.getName(), cmd)
                .orElseThrow(() -> new NotImplementedException("resource  {0} get not implemented", child.getName()));
        validateAction(action, SpringServiceDescriptor.ActionDescriptor.ActionType.QUERY_HANDLER);
        QueryWrapper request = QueryWrapper.builder()
                .withQuery(action.getName())
                .withVersion(version)
                .withRootId(rootId)
                .withId(childId).withTenantId(tenantId).build();

        return cluster.requestReply(serviceDesc.getServiceName(), request)
                .onErrorMap(this::onError);
    }

    @Override
    protected void validateAction(SpringServiceDescriptor.ActionDescriptor action, SpringServiceDescriptor.ActionDescriptor.ActionType type) {
        super.validateAction(action, type);

        if(!action.isPubliclyAccessible()) {
            throw new BadRequestException("action {} is restricted to access through public", action.getName());
        }
    }
}
