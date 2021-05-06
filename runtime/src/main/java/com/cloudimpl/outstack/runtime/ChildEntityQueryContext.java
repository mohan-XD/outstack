/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.cloudimpl.outstack.runtime;

import com.cloudimpl.outstack.runtime.domainspec.ChildEntity;
import com.cloudimpl.outstack.runtime.domainspec.RootEntity;

/**
 *
 * @author nuwan
 * @param <R>
 * @param <T>
 */
public interface ChildEntityQueryContext<R extends RootEntity,T extends ChildEntity<R>> extends EntityQueryContext<T>{
     <R extends RootEntity> R getRoot();
}
