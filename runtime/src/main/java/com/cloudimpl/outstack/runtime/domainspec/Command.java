/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.cloudimpl.outstack.runtime.domainspec;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 *
 * @author nuwansa
 */
public abstract class Command implements Input, ICommand {

    private String _rootId;
    private String _id;
    private String _tenantId;
    private String _version;
    private final String _commandName;
    private List<MultipartFile> _files;

    public Command(Builder builder) {
        this._rootId = builder.rootId;
        this._tenantId = builder.tenantId;
        this._id = builder.id;
        this._version = builder.version;
        this._commandName = builder.commandName;
        this._files = builder.files;
    }

    public final String rootId() {
        return _rootId;
    }

    protected void setRootId(String rootId) {
        this._rootId = rootId;
    }

    protected void setId(String id) {
        this._id = id;
    }

    public final String id() {
        return this._id;
    }

    protected void setTenantId(String tenantId) {
        this._tenantId = tenantId;
    }

    protected void setVersion(String version) {
        this._version = version;
    }

    public List<MultipartFile> get_files() {
        return _files;
    }

    public void set_files(List<MultipartFile> _files) {
        this._files = _files;
    }

    @Override
    public final String commandName() {
        return _commandName;
    }

    @Override
    public final <T extends Command> T unwrap(Class<T> type) {
        return (T) this;
    }

    @Override
    public final String tenantId() {
        return _tenantId;
    }

    @Override
    public final String version() {
        return _version;
    }

    public static class Builder {

        protected String rootId;
        protected String id;
        protected String tenantId;
        protected String version;
        protected String commandName;
        protected List<MultipartFile> files;

        public Builder withRootId(String rootId) {
            this.rootId = rootId;
            return this;
        }

        public Builder withId(String id) {
            this.id = id;
            return this;
        }

        public Builder withVersion(String version) {
            this.version = version;
            return this;
        }

        public Builder withTenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder withCommandName(String commandName) {
            this.commandName = commandName;
            return this;
        }

        public Builder withFiles(List<MultipartFile> files) {
            this.files = files;
            return this;
        }

        public <T extends Command> T build() {
            return (T) new Command(this) {
            };
        }
    }
}
