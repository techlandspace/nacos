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

package com.alibaba.nacos.test.naming;

import com.alibaba.nacos.Nacos;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.common.remote.client.RpcConstants;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer.MethodName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.alibaba.nacos.test.naming.NamingBase.randomDomainName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * NamingTlsServiceTls_ITCase.
 *
 * @author githucheng2978.
 * @date .
 **/
@TestMethodOrder(MethodName.class)
@SpringBootTest(classes = Nacos.class, properties = {"server.servlet.context-path=/nacos",
        RpcConstants.NACOS_SERVER_RPC + ".enableTls=true", RpcConstants.NACOS_SERVER_RPC + ".compatibility=false",
        RpcConstants.NACOS_SERVER_RPC + ".certChainFile=test-server-cert.pem",
        RpcConstants.NACOS_SERVER_RPC + ".certPrivateKey=test-server-key.pem"}, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@Disabled("TODO, Fix cert expired problem")
class TlsServiceTlsNamingITCase {
    
    
    @LocalServerPort
    private int port;
    
    @Test
    void TlsServerAndPlainClient() throws NacosException {
        assertThrows(NacosException.class, () -> {
            
            Instance instance = new Instance();
            instance.setIp("127.0.0.1");
            instance.setPort(8081);
            instance.setWeight(2);
            instance.setClusterName(Constants.DEFAULT_CLUSTER_NAME);
            NamingService namingService = NamingFactory.createNamingService("127.0.0.1" + ":" + port);
            Map<String, String> map = new HashMap<String, String>();
            map.put("netType", "external-update");
            map.put("version", "2.0");
            namingService.registerInstance(randomDomainName(), instance);
            namingService.shutDown();
            
        });
        
    }
    
    @Test
    void TlsServerAndTlsClientTrustCa() throws NacosException {
        String serviceName = randomDomainName();
        System.setProperty(RpcConstants.RPC_CLIENT_TLS_ENABLE, "true");
        System.setProperty(RpcConstants.RPC_CLIENT_TLS_TRUST_COLLECTION_CHAIN_PATH, "test-ca-cert.pem");
        Instance instance = new Instance();
        instance.setIp("127.0.0.1");
        instance.setPort(8081);
        instance.setWeight(2);
        instance.setClusterName(Constants.DEFAULT_CLUSTER_NAME);
        NamingService namingService = NamingFactory.createNamingService("127.0.0.1" + ":" + port);
        Map<String, String> map = new HashMap<String, String>();
        map.put("netType", "external-update");
        map.put("version", "2.0");
        instance.setMetadata(map);
        try {
            TimeUnit.SECONDS.sleep(1L);
            namingService.registerInstance(serviceName, instance);
            TimeUnit.SECONDS.sleep(3L);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        List<Instance> instances = namingService.getAllInstances(serviceName, false);
        assertEquals(1, instances.size());
        assertEquals("2.0", instances.get(0).getMetadata().get("version"));
        namingService.shutDown();
        
    }
    
    @Test
    void TlsServerAndTlsClientAll() throws NacosException {
        String serviceName = randomDomainName();
        System.setProperty(RpcConstants.RPC_CLIENT_TLS_ENABLE, "true");
        System.setProperty(RpcConstants.RPC_CLIENT_TLS_TRUST_ALL, "true");
        Instance instance = new Instance();
        instance.setIp("127.0.0.1");
        instance.setPort(8081);
        instance.setWeight(2);
        instance.setClusterName(Constants.DEFAULT_CLUSTER_NAME);
        NamingService namingService = NamingFactory.createNamingService("127.0.0.1" + ":" + port);
        Map<String, String> map = new HashMap<String, String>();
        map.put("netType", "external-update");
        map.put("version", "2.0");
        instance.setMetadata(map);
        namingService.registerInstance(serviceName, instance);
        try {
            TimeUnit.SECONDS.sleep(3L);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        List<Instance> instances = namingService.getAllInstances(serviceName, false);
        assertEquals(1, instances.size());
        assertEquals("2.0", instances.get(0).getMetadata().get("version"));
        System.out.println(instances.get(0));
        namingService.shutDown();
    }
}
