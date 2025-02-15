/*
 * Copyright 2021 nuwan.
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
package com.cloudimpl.outstack.spring.service.iam;

import com.cloudimpl.outstack.common.RouterType;
import com.cloudimpl.outstack.core.annon.CloudFunction;
import com.cloudimpl.outstack.core.annon.Router;
import com.cloudimpl.outstack.runtime.EventRepositoryFactory;
import com.cloudimpl.outstack.runtime.domain.PolicyStatement;

import com.cloudimpl.outstack.spring.component.SpringService;

/**
 *
 * @author nuwan
 */
@CloudFunction(name = "PolicyStatementService")
@Router(routerType = RouterType.ROUND_ROBIN)
public class PolicyStatementService extends SpringService<PolicyStatement> {

    static {
        $(CreatePolicyStatement.class);
    }

    public PolicyStatementService(EventRepositoryFactory factory) {
        super(factory);
    }

}
