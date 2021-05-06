/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.cloudimpl.outstack.service.example;

import com.cloudimpl.outstack.domain.example.Organization;
import com.cloudimpl.outstack.runtime.EntityQueryContext;
import com.cloudimpl.outstack.runtime.EntityQueryHandler;
import com.cloudimpl.outstack.runtime.domainspec.QueryByIdReuuest;

/**
 *
 * @author nuwan
 */
public class GetOrganization extends EntityQueryHandler<Organization,QueryByIdReuuest, Organization>{

    @Override
    protected Organization execute(EntityQueryContext<Organization> context, QueryByIdReuuest query) {
        return context.getById(query.rootId()).orElseThrow();
    }
    
}
