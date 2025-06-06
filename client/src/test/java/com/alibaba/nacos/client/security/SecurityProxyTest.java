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

package com.alibaba.nacos.client.security;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.address.AbstractServerListManager;
import com.alibaba.nacos.client.auth.impl.NacosAuthLoginConstant;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.plugin.auth.api.LoginIdentityContext;
import com.alibaba.nacos.plugin.auth.api.RequestResource;
import com.alibaba.nacos.plugin.auth.spi.client.AbstractClientAuthService;
import com.alibaba.nacos.plugin.auth.spi.client.ClientAuthPluginManager;
import com.alibaba.nacos.plugin.auth.spi.client.ClientAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
// todo  remove strictness lenient
@MockitoSettings(strictness = Strictness.LENIENT)
class SecurityProxyTest {
    
    private SecurityProxy securityProxy;
    
    @Mock
    private NacosRestTemplate nacosRestTemplate;
    
    @BeforeEach
    void setUp() throws Exception {
        //given
        HttpRestResult<Object> result = new HttpRestResult<>();
        result.setData("{\"accessToken\":\"ttttttttttttttttt\",\"tokenTtl\":1000}");
        result.setCode(200);
        when(nacosRestTemplate.postForm(any(), (Header) any(), any(), any(), any())).thenReturn(result);
        
        List<String> serverList = new ArrayList<>();
        serverList.add("localhost");
        NacosClientProperties properties = NacosClientProperties.PROTOTYPE.derive(new Properties());
        AbstractServerListManager serverListManager = new AbstractServerListManager(properties) {
            @Override
            protected String getModuleName() {
                return "Test";
            }
            
            @Override
            protected NacosRestTemplate getNacosRestTemplate() {
                return nacosRestTemplate;
            }
            
            @Override
            public String genNextServer() {
                return serverList.get(0);
            }
            
            @Override
            public String getCurrentServer() {
                return serverList.get(0);
            }
            
            @Override
            public List<String> getServerList() {
                return serverList;
            }
        };
        securityProxy = new SecurityProxy(serverListManager, nacosRestTemplate);
    }
    
    @Test
    void testLoginClientAuthService() throws Exception {
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.USERNAME, "aaa");
        properties.setProperty(PropertyKeyConst.PASSWORD, "123456");
        securityProxy.login(properties);
        verify(nacosRestTemplate).postForm(any(), (Header) any(), any(), any(), any());
    }
    
    @Test
    void testGetIdentityContext() {
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.USERNAME, "aaa");
        properties.setProperty(PropertyKeyConst.PASSWORD, "123456");
        securityProxy.login(properties);
        //when
        Map<String, String> keyMap = securityProxy.getIdentityContext(null);
        //then
        assertEquals("ttttttttttttttttt", keyMap.get(NacosAuthLoginConstant.ACCESSTOKEN));
    }
    
    @Test
    void testLoginWithoutAnyPlugin() throws NoSuchFieldException, IllegalAccessException {
        Field clientAuthPluginManagerField = SecurityProxy.class.getDeclaredField("clientAuthPluginManager");
        clientAuthPluginManagerField.setAccessible(true);
        ClientAuthPluginManager clientAuthPluginManager = mock(ClientAuthPluginManager.class);
        clientAuthPluginManagerField.set(securityProxy, clientAuthPluginManager);
        when(clientAuthPluginManager.getAuthServiceSpiImplSet()).thenReturn(Collections.emptySet());
        securityProxy.login(new Properties());
        Map<String, String> header = securityProxy.getIdentityContext(new RequestResource());
        assertTrue(header.isEmpty());
    }
    
    @Test
    void testReLogin() throws NoSuchFieldException, IllegalAccessException {
        Field clientAuthPluginManagerField = SecurityProxy.class.getDeclaredField("clientAuthPluginManager");
        clientAuthPluginManagerField.setAccessible(true);
        ClientAuthPluginManager clientAuthPluginManager = mock(ClientAuthPluginManager.class);
        clientAuthPluginManagerField.set(securityProxy, clientAuthPluginManager);
        when(clientAuthPluginManager.getAuthServiceSpiImplSet()).thenReturn(
                Collections.singleton(new AbstractClientAuthService() {
                    
                    private LoginIdentityContext loginIdentityContext;
                    
                    @Override
                    public Boolean login(Properties properties) {
                        return null;
                    }
                    
                    @Override
                    public LoginIdentityContext getLoginIdentityContext(RequestResource resource) {
                        if (loginIdentityContext == null) {
                            loginIdentityContext = new LoginIdentityContext();
                        }
                        return loginIdentityContext;
                    }
                    
                    @Override
                    public void shutdown() throws NacosException {
                    
                    }
                }));
        securityProxy.reLogin();
        Map<String, String> identityContext = securityProxy.getIdentityContext(new RequestResource());
        assertEquals(identityContext.get(NacosAuthLoginConstant.RELOGINFLAG), "true");
    }
    
    @Test
    void testReLoginWithEmptyPlugin() throws NoSuchFieldException, IllegalAccessException {
        Field clientAuthPluginManagerField = SecurityProxy.class.getDeclaredField("clientAuthPluginManager");
        clientAuthPluginManagerField.setAccessible(true);
        ClientAuthPluginManager clientAuthPluginManager = mock(ClientAuthPluginManager.class);
        clientAuthPluginManagerField.set(securityProxy, clientAuthPluginManager);
        when(clientAuthPluginManager.getAuthServiceSpiImplSet()).thenReturn(Collections.emptySet());
        securityProxy.reLogin();
        Map<String, String> identityContext = securityProxy.getIdentityContext(new RequestResource());
        assertFalse(identityContext.containsKey(NacosAuthLoginConstant.RELOGINFLAG));
    }
    
    @Test
    void testReLoginWithException() throws NoSuchFieldException, IllegalAccessException {
        Field clientAuthPluginManagerField = SecurityProxy.class.getDeclaredField("clientAuthPluginManager");
        clientAuthPluginManagerField.setAccessible(true);
        ClientAuthPluginManager clientAuthPluginManager = mock(ClientAuthPluginManager.class);
        clientAuthPluginManagerField.set(securityProxy, clientAuthPluginManager);
        ClientAuthService mockClientAuthService = mock(ClientAuthService.class);
        when(mockClientAuthService.getLoginIdentityContext(any())).thenThrow(new RuntimeException("test"));
        when(clientAuthPluginManager.getAuthServiceSpiImplSet()).thenReturn(Collections.singleton(mockClientAuthService));
        assertDoesNotThrow(() -> securityProxy.reLogin());
    }
}
