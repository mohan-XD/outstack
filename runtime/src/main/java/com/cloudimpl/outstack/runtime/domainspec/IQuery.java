/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.cloudimpl.outstack.runtime.domainspec;

/**
 *
 * @author nuwan
 */
public interface IQuery {
    String queryName();
    String version();
    <T extends Query> T unwrap(Class<T> type);
}
