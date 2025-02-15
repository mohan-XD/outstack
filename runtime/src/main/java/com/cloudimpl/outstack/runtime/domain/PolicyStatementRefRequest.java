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
package com.cloudimpl.outstack.runtime.domain;

import com.cloudimpl.outstack.runtime.domainspec.Command;

/**
 *
 * @author nuwan
 */
public class PolicyStatementRefRequest extends Command {

    private final String policyName;
    private final String policyStmtName;
    public PolicyStatementRefRequest(Builder builder) {
        super(builder);
        this.policyName = builder.policyName;
        this.policyStmtName = builder.policyStmtName;
    }

    public String getPolicyName() {
        return policyName;
    }

    public String getPolicyStmtName() {
        return policyStmtName;
    }

    public static Builder builder()
    {
        return new Builder();
    }
    
    public static final class Builder extends Command.Builder {

        private String policyName; 
        private String policyStmtName;
        
        public Builder withPolicyName(String policyName)
        {
            this.policyName = policyName;
            return this;
        }
        
        public Builder withPolicyStmtName(String stmtName)
        {
            this.policyStmtName = stmtName;
            return this;
        }
        
        @Override
        public PolicyStatementRefRequest build()
        {
            return new PolicyStatementRefRequest(this);
        }
    }
}
