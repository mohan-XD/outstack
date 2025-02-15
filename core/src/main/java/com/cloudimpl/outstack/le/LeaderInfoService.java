/*
 * Copyright 2020 nuwansa.
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
package com.cloudimpl.outstack.le;

import com.cloudimpl.outstack.common.CloudMessage;
import com.cloudimpl.outstack.common.RouterType;
import com.cloudimpl.outstack.core.Inject;
import com.cloudimpl.outstack.core.annon.CloudFunction;
import com.cloudimpl.outstack.core.annon.Router;
import java.util.function.Function;
import reactor.core.publisher.Flux;

/**
 *
 * @author nuwansa
 */
@CloudFunction(name = "LeaderInfoService")
@Router(routerType = RouterType.NODE_ID)
public class LeaderInfoService implements Function<CloudMessage, Flux<LeaderInfoResponse>>{

    private final LeaderElectionManager elecMan;

    @Inject
    public LeaderInfoService(LeaderElectionManager elecMan) {
        this.elecMan = elecMan;
    }
    
    
    @Override
    public Flux<LeaderInfoResponse> apply(CloudMessage req) {
        LeaderInfoRequest leaderReq = req.data();
        return this.elecMan.flux().map(e->e.getValue()).filter(info->info.getLeaderGroup().equals(leaderReq.getLeaderGroup()))
                .map(info->new LeaderInfoResponse(info));
    }
    
}
