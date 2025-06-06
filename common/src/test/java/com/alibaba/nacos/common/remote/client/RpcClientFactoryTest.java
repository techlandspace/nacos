/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.common.remote.client;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.remote.ConnectionType;
import com.alibaba.nacos.common.remote.client.grpc.DefaultGrpcClientConfig;
import com.alibaba.nacos.common.remote.client.grpc.GrpcClientConfig;
import com.alibaba.nacos.common.remote.client.grpc.GrpcClusterClient;
import com.alibaba.nacos.common.utils.CollectionUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RpcClientFactoryTest {
    
    static Field clientMapField;
    
    @Mock
    RpcClient rpcClient;
    
    @Mock(lenient = true)
    RpcClientTlsConfig clusterClientTlsConfig;
    
    @Mock(lenient = true)
    RpcClientTlsConfig rpcClientTlsConfig;
    
    @BeforeAll
    static void setUpBeforeClass()
            throws NoSuchFieldException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        clientMapField = RpcClientFactory.class.getDeclaredField("CLIENT_MAP");
        clientMapField.setAccessible(true);
        Method getDeclaredFields0 = Class.class.getDeclaredMethod("getDeclaredFields0", boolean.class);
        getDeclaredFields0.setAccessible(true);
        Field[] fields = (Field[]) getDeclaredFields0.invoke(Field.class, false);
        Field modifiersField1 = null;
        for (Field each : fields) {
            if ("modifiers".equals(each.getName())) {
                modifiersField1 = each;
            }
        }
        if (modifiersField1 != null) {
            modifiersField1.setAccessible(true);
            modifiersField1.setInt(clientMapField, clientMapField.getModifiers() & ~Modifier.FINAL);
        }
    }
    
    @AfterEach
    void tearDown() throws IllegalAccessException {
        clientMapField.set(null, new ConcurrentHashMap<>());
    }
    
    @Test
    void testGetAllClientEntries() throws IllegalAccessException {
        assertTrue(RpcClientFactory.getAllClientEntries().isEmpty());
        
        clientMapField.set(null, Collections.singletonMap("testClient", rpcClient));
        assertEquals(1, RpcClientFactory.getAllClientEntries().size());
    }
    
    @Test
    void testDestroyClientWhenClientExistThenRemoveAndShutDownRpcClient() throws IllegalAccessException, NacosException {
        clientMapField.set(null, new ConcurrentHashMap<>(Collections.singletonMap("testClient", rpcClient)));
        
        RpcClientFactory.destroyClient("testClient");
        
        assertTrue(RpcClientFactory.getAllClientEntries().isEmpty());
        verify(rpcClient).shutdown();
    }
    
    @Test
    void testDestroyClientWhenClientNotExistThenDoNothing() throws IllegalAccessException, NacosException {
        clientMapField.set(null, new ConcurrentHashMap<>(Collections.singletonMap("testClient", rpcClient)));
        
        RpcClientFactory.destroyClient("notExistClientName");
        
        Map.Entry<String, RpcClient> element = CollectionUtils.getOnlyElement(RpcClientFactory.getAllClientEntries());
        assertEquals("testClient", element.getKey());
        assertEquals(rpcClient, element.getValue());
        verify(rpcClient, times(0)).shutdown();
    }
    
    @Test
    void testGetClient() throws IllegalAccessException {
        // may be null
        assertNull(RpcClientFactory.getClient("notExistClientName"));
        
        clientMapField.set(null, new ConcurrentHashMap<>(Collections.singletonMap("testClient", rpcClient)));
        assertEquals(rpcClient, RpcClientFactory.getClient("testClient"));
    }
    
    @Test
    void testCreateClientWhenNotCreatedThenCreate() {
        RpcClient client = RpcClientFactory.createClient("testClient", ConnectionType.GRPC,
                Collections.singletonMap("labelKey", "labelValue"));
        Map<String, String> labesMap = new HashMap<>();
        labesMap.put("labelKey", "labelValue");
        labesMap.put("tls.enable", "false");
        assertEquals(labesMap, client.rpcClientConfig.labels());
        assertEquals(ConnectionType.GRPC, client.getConnectionType());
        assertEquals("testClient", CollectionUtils.getOnlyElement(RpcClientFactory.getAllClientEntries()).getKey());
    }
    
    @Test
    void testCreateClientWhenAlreadyCreatedThenNotCreateAgain() {
        RpcClient client1 = RpcClientFactory.createClient("testClient", ConnectionType.GRPC,
                Collections.singletonMap("labelKey", "labelValue"));
        RpcClient client2 = RpcClientFactory.createClient("testClient", ConnectionType.GRPC,
                Collections.singletonMap("labelKey", "labelValue"));
        
        assertEquals(client1, client2);
        assertEquals(1, RpcClientFactory.getAllClientEntries().size());
    }
    
    @Test
    void testCreatedClientWhenConnectionTypeNotMappingThenThrowException() {
        assertThrows(Exception.class, () -> {
            RpcClientFactory.createClient("testClient", mock(ConnectionType.class),
                    Collections.singletonMap("labelKey", "labelValue"));
        });
    }
    
    @Test
    void testCreateClusterClientWhenNotCreatedThenCreate() {
        RpcClient client = RpcClientFactory.createClusterClient("testClient", ConnectionType.GRPC,
                Collections.singletonMap("labelKey", "labelValue"));
        Map<String, String> labesMap = new HashMap<>();
        labesMap.put("labelKey", "labelValue");
        labesMap.put("tls.enable", "false");
        assertEquals(labesMap, client.rpcClientConfig.labels());
        assertEquals(ConnectionType.GRPC, client.getConnectionType());
        assertEquals("testClient", CollectionUtils.getOnlyElement(RpcClientFactory.getAllClientEntries()).getKey());
    }
    
    @Test
    void testCreateClusterClientWhenAlreadyCreatedThenNotCreateAgain() {
        RpcClient client1 = RpcClientFactory.createClusterClient("testClient", ConnectionType.GRPC,
                Collections.singletonMap("labelKey", "labelValue"));
        RpcClient client2 = RpcClientFactory.createClusterClient("testClient", ConnectionType.GRPC,
                Collections.singletonMap("labelKey", "labelValue"));
        
        assertEquals(client1, client2);
        assertEquals(1, RpcClientFactory.getAllClientEntries().size());
    }
    
    @Test
    void testCreatedClusterClientWhenConnectionTypeNotMappingThenThrowException() {
        assertThrows(Exception.class, () -> {
            RpcClientFactory.createClusterClient("testClient", mock(ConnectionType.class),
                    Collections.singletonMap("labelKey", "labelValue"));
        });
    }
    
    @Test
    void testCreateClusterClientTsl() {
        Mockito.when(clusterClientTlsConfig.getEnableTls()).thenReturn(true);
        RpcClient client = RpcClientFactory.createClusterClient("testClient", ConnectionType.GRPC,
                Collections.singletonMap("labelKey", "labelValue"), clusterClientTlsConfig);
        Map<String, String> labesMap = new HashMap<>();
        labesMap.put("labelKey", "labelValue");
        labesMap.put("tls.enable", "true");
        assertEquals(labesMap, client.rpcClientConfig.labels());
        assertEquals(ConnectionType.GRPC, client.getConnectionType());
        assertEquals("testClient", CollectionUtils.getOnlyElement(RpcClientFactory.getAllClientEntries()).getKey());
    }
    
    @Test
    void testCreateClientTsl() {
        Mockito.when(rpcClientTlsConfig.getEnableTls()).thenReturn(true);
        RpcClient client = RpcClientFactory.createClient("testClient", ConnectionType.GRPC,
                Collections.singletonMap("labelKey", "labelValue"), rpcClientTlsConfig);
        Map<String, String> labesMap = new HashMap<>();
        labesMap.put("labelKey", "labelValue");
        labesMap.put("tls.enable", "true");
        assertEquals(labesMap, client.rpcClientConfig.labels());
        assertEquals(ConnectionType.GRPC, client.getConnectionType());
        assertEquals("testClient", CollectionUtils.getOnlyElement(RpcClientFactory.getAllClientEntries()).getKey());
    }
    
    @Test
    void testCreateClientWithProperties() {
        Mockito.when(rpcClientTlsConfig.getEnableTls()).thenReturn(true);
        Properties properties = new Properties();
        Map<String, String> labels = new HashMap<>();
        labels.put("tls.enable", "false");
        labels.put("labelKey", "labelValue");
        GrpcClientConfig grpcClientConfig = RpcClientConfigFactory.getInstance()
                .createGrpcClientConfig(properties, labels);
        RpcClient testClient = RpcClientFactory.createClient("testClient", ConnectionType.GRPC, grpcClientConfig);
        assertEquals(testClient.getLabels(), labels);
        assertEquals(testClient.getConnectionType(), ConnectionType.GRPC);
        assertEquals(testClient.getName(), "testClient");
    }
    
    @Test
    void testCreateClusterClientWithProperties() {
        Mockito.when(rpcClientTlsConfig.getEnableTls()).thenReturn(true);
        Properties properties = new Properties();
        properties.setProperty("nacos.remote.client.grpc.maxinbound.message.size", "100000");
        Map<String, String> labels = new HashMap<>();
        labels.put("tls.enable", "false");
        labels.put("labelKey", "labelValue");
        GrpcClientConfig clientConfig = DefaultGrpcClientConfig.newBuilder().buildClusterFromProperties(properties)
                .setLabels(labels).build();
        GrpcClusterClient testClient = (GrpcClusterClient) RpcClientFactory.createClusterClient("testClient",
                ConnectionType.GRPC, clientConfig);
        assertEquals(testClient.getLabels(), labels);
        assertEquals(testClient.getConnectionType(), ConnectionType.GRPC);
        assertEquals(testClient.getName(), "testClient");
        GrpcClientConfig testConfig = (GrpcClientConfig) testClient.rpcClientConfig;
        assertEquals(testConfig.maxInboundMessageSize(), 100000);
    }
}
