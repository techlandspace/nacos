/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.core.v2.event.client;

import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.naming.core.v2.client.Client;
import com.alibaba.nacos.naming.core.v2.pojo.Service;

import java.util.Set;

/**
 * Operation client event.
 *
 * @author xiweng.yy
 */
public class ClientOperationEvent extends Event {
    
    private static final long serialVersionUID = -4582413232902517619L;
    
    private final String clientId;
    
    private final Service service;
    
    public ClientOperationEvent(String clientId, Service service) {
        this.clientId = clientId;
        this.service = service;
    }
    
    public String getClientId() {
        return clientId;
    }
    
    public Service getService() {
        return service;
    }
    
    /**
     * Client register service event.
     */
    public static class ClientRegisterServiceEvent extends ClientOperationEvent {
        
        private static final long serialVersionUID = 3412396514440368087L;
        
        public ClientRegisterServiceEvent(Service service, String clientId) {
            super(clientId, service);
        }
    }
    
    /**
     * Client deregister service event.
     */
    public static class ClientDeregisterServiceEvent extends ClientOperationEvent {
        
        private static final long serialVersionUID = -4518919987813223120L;
        
        public ClientDeregisterServiceEvent(Service service, String clientId) {
            super(clientId, service);
        }
    }
    
    /**
     * Client subscribe service event.
     */
    public static class ClientSubscribeServiceEvent extends ClientOperationEvent {
        
        private static final long serialVersionUID = -4518919987813223120L;
        
        public ClientSubscribeServiceEvent(Service service, String clientId) {
            super(clientId, service);
        }
    }
    
    /**
     * Client unsubscribe service event.
     */
    public static class ClientUnsubscribeServiceEvent extends ClientOperationEvent {
        
        private static final long serialVersionUID = -4518919987813223120L;
        
        public ClientUnsubscribeServiceEvent(Service service, String clientId) {
            super(clientId, service);
        }
    }
    
    /**
     * Client fuzzy watch service event.
     */
    public static class ClientFuzzyWatchEvent extends ClientOperationEvent {
        
        private static final long serialVersionUID = -4518919987813223119L;
        
        /**
         * client watched pattern.
         */
        private final String groupKeyPattern;
        
        /**
         * client side received group keys.
         */
        private Set<String> clientReceivedServiceKeys;
        
        /**
         * is fuzzy watch initializing.
         */
        private boolean isInitializing;
        
        public ClientFuzzyWatchEvent(String groupKeyPattern, String clientId, Set<String> clientReceivedServiceKeys,
                boolean isInitializing) {
            super(clientId, null);
            this.groupKeyPattern = groupKeyPattern;
            this.clientReceivedServiceKeys = clientReceivedServiceKeys;
            this.isInitializing = isInitializing;
        }
        
        public String getGroupKeyPattern() {
            return groupKeyPattern;
        }
        
        public Set<String> getClientReceivedServiceKeys() {
            return clientReceivedServiceKeys;
        }
        
        public boolean isInitializing() {
            return isInitializing;
        }
    }
    
    public static class ClientReleaseEvent extends ClientOperationEvent {
        
        private static final long serialVersionUID = -281486927726245701L;
        
        private final Client client;
        
        private final boolean isNative;
        
        public ClientReleaseEvent(Client client, boolean isNative) {
            super(client.getClientId(), null);
            this.client = client;
            this.isNative = isNative;
        }
        
        public Client getClient() {
            return client;
        }
        
        public boolean isNative() {
            return isNative;
        }
    }
}
