/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.cloudimpl.outstack.runtime;

import com.cloudimpl.outstack.runtime.domainspec.ChildEntity;
import com.cloudimpl.outstack.runtime.domainspec.Entity;
import com.cloudimpl.outstack.runtime.domainspec.RootEntity;
import java.util.Optional;

/**
 *
 * @author nuwan
 * @param <T>
 */
public interface EntityQueryContext<T extends Entity>{
    Optional<T> getByEntityId(String id); 
    Optional<T> getById(String id);
    
    <R extends RootEntity> RootEntityQueryContext<R> asRootQueryContext();


    <R extends RootEntity,K extends ChildEntity<R>> ChildEntityQueryContext<R,K> asChildQueryContext() ;
    
}
