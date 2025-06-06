/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.controllers.v2;

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.core.remote.ConnectionManager;
import com.alibaba.nacos.naming.BaseTest;
import com.alibaba.nacos.naming.core.ClientServiceImpl;
import com.alibaba.nacos.naming.core.DistroMapper;
import com.alibaba.nacos.naming.core.v2.client.impl.ConnectionBasedClient;
import com.alibaba.nacos.naming.core.v2.client.impl.IpPortBasedClient;
import com.alibaba.nacos.naming.core.v2.client.manager.ClientManager;
import com.alibaba.nacos.naming.core.v2.index.ClientServiceIndexesManager;
import com.alibaba.nacos.naming.core.v2.pojo.InstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.pojo.Subscriber;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClientInfoControllerV2Test extends BaseTest {
    
    private static final String URL =
            UtilsAndCommons.DEFAULT_NACOS_NAMING_CONTEXT_V2 + UtilsAndCommons.NACOS_NAMING_CLIENT_CONTEXT;
    
    ClientInfoControllerV2 clientInfoControllerV2;
    
    @Mock
    private ClientManager clientManager;
    
    @Mock
    private ConnectionManager connectionManager;
    
    @InjectMocks
    private ClientServiceImpl clientServiceV2Impl;
    
    @Mock
    private ClientServiceIndexesManager clientServiceIndexesManager;
    
    @Mock
    private DistroMapper distroMapper;
    
    private MockMvc mockmvc;
    
    private IpPortBasedClient ipPortBasedClient;
    
    private ConnectionBasedClient connectionBasedClient;
    
    @BeforeEach
    public void before() {
        when(clientManager.allClientId()).thenReturn(Arrays.asList("127.0.0.1:8080#test1", "test2#test2"));
        when(clientManager.contains(anyString())).thenReturn(true);
        clientInfoControllerV2 = new ClientInfoControllerV2(clientManager, clientServiceV2Impl);
        mockmvc = MockMvcBuilders.standaloneSetup(clientInfoControllerV2).build();
        ipPortBasedClient = new IpPortBasedClient("127.0.0.1:8080#test1", false);
        connectionBasedClient = new ConnectionBasedClient("test2", true, 1L);
    }
    
    @Test
    void testGetClientList() throws Exception {
        MockHttpServletRequestBuilder mockHttpServletRequestBuilder = MockMvcRequestBuilders.get(URL + "/list");
        MockHttpServletResponse response = mockmvc.perform(mockHttpServletRequestBuilder).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        JsonNode jsonNode = JacksonUtils.toObj(response.getContentAsString()).get("data");
        assertEquals(2, jsonNode.size());
    }
    
    @Test
    void testGetClientDetail() throws Exception {
        when(clientManager.getClient("test1")).thenReturn(ipPortBasedClient);
        MockHttpServletRequestBuilder mockHttpServletRequestBuilder = MockMvcRequestBuilders.get(URL)
                .param("clientId", "test1");
        MockHttpServletResponse response = mockmvc.perform(mockHttpServletRequestBuilder).andReturn().getResponse();
        assertEquals(200, response.getStatus());
    }
    
    @Test
    void testGetPublishedServiceList() throws Exception {
        Service service = Service.newService("test", "test", "test");
        when(clientManager.getClient("test2")).thenReturn(connectionBasedClient);
        connectionBasedClient.addServiceInstance(service, new InstancePublishInfo("127.0.0.1", 8848));
        MockHttpServletRequestBuilder mockHttpServletRequestBuilder = MockMvcRequestBuilders.get(URL + "/publish/list")
                .param("clientId", "test2");
        mockmvc.perform(mockHttpServletRequestBuilder)
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.length()").value(1));
    }
    
    @Test
    void testGetPublishedClientList() throws Exception {
        String baseTestKey = "nacos-getPublishedClientList-test";
        // single instance
        final Service service = Service.newService(baseTestKey, baseTestKey, baseTestKey);
        
        when(clientManager.getClient("test1")).thenReturn(connectionBasedClient);
        when(clientManager.getClient("test")).thenReturn(connectionBasedClient);
        connectionBasedClient.addServiceInstance(service, new InstancePublishInfo("127.0.0.1", 8848));
        when(clientServiceIndexesManager.getAllClientsRegisteredService(service)).thenReturn(
                Collections.singletonList("test"));
        MockHttpServletRequestBuilder mockHttpServletRequestBuilder = MockMvcRequestBuilders.get(
                        URL + "/service/publisher/list").param("namespaceId", baseTestKey).param("groupName", baseTestKey)
                .param("serviceName", baseTestKey).param("ip", "127.0.0.1").param("port", "8848");
        mockmvc.perform(mockHttpServletRequestBuilder)
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.length()").value(1));
    }
    
    @Test
    void testGetSubscribeClientList() throws Exception {
        String baseTestKey = "nacos-getSubScribedClientList-test";
        // ip port match
        Service service = Service.newService(baseTestKey, baseTestKey, baseTestKey);
        when(clientServiceIndexesManager.getAllClientsSubscribeService(service)).thenReturn(Arrays.asList("test"));
        when(clientManager.getClient("test")).thenReturn(ipPortBasedClient);
        Subscriber subscriber = mock(Subscriber.class);
        when(subscriber.getIp()).thenReturn("127.0.0.1");
        when(subscriber.getPort()).thenReturn(8848);
        ipPortBasedClient.addServiceSubscriber(service, subscriber);
        MockHttpServletRequestBuilder mockHttpServletRequestBuilder = MockMvcRequestBuilders.get(
                        URL + "/service/subscriber/list").param("namespaceId", baseTestKey).param("groupName", baseTestKey)
                .param("serviceName", baseTestKey).param("ip", "127.0.0.1").param("port", "8848");
        mockmvc.perform(mockHttpServletRequestBuilder)
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.length()").value(1));
        // ip port not match
        mockHttpServletRequestBuilder = MockMvcRequestBuilders.get(URL + "/service/subscriber/list")
                .param("namespaceId", baseTestKey).param("groupName", baseTestKey).param("serviceName", baseTestKey)
                .param("ip", "127.0.0.1").param("port", "8849");
        mockmvc.perform(mockHttpServletRequestBuilder)
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.length()").value(0));
        // ip port is null
        mockHttpServletRequestBuilder = MockMvcRequestBuilders.get(URL + "/service/subscriber/list")
                .param("namespaceId", baseTestKey).param("groupName", baseTestKey).param("serviceName", baseTestKey);
        mockmvc.perform(mockHttpServletRequestBuilder)
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.length()").value(1));
    }
}
