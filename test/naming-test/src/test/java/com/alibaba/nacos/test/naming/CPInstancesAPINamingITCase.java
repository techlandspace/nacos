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
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ListView;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.test.base.Params;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.net.URL;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author nkorange
 */
@SpringBootTest(classes = Nacos.class, properties = {
        "server.servlet.context-path=/nacos"}, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class CPInstancesAPINamingITCase extends NamingBase {
    
    private NamingService naming;
    
    private NamingService naming1;
    
    private NamingService naming2;
    
    @LocalServerPort
    private int port;
    
    @BeforeEach
    void setUp() throws Exception {
        String url = String.format("http://localhost:%d/", port);
        this.base = new URL(url);
        
        naming = NamingFactory.createNamingService("127.0.0.1" + ":" + port);
        
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.NAMESPACE, TEST_NAMESPACE_1);
        properties.put(PropertyKeyConst.SERVER_ADDR, "127.0.0.1" + ":" + port);
        naming1 = NamingFactory.createNamingService(properties);
        
        properties = new Properties();
        properties.put(PropertyKeyConst.NAMESPACE, TEST_NAMESPACE_2);
        properties.put(PropertyKeyConst.SERVER_ADDR, "127.0.0.1" + ":" + port);
        naming2 = NamingFactory.createNamingService(properties);
        isNamingServerReady();
    }
    
    @AfterEach
    void cleanup() throws Exception {
    }
    
    /**
     * @TCDescription : 根据serviceName创建服务, 通过registerInstance接口注册实例, ephemeral为true
     * @TestStep :
     * @ExpectResult :
     */
    @Test
    void registerInstanceEphemeralTrue() throws Exception {
        String serviceName = NamingBase.randomDomainName();
        
        Instance instance = new Instance();
        instance.setEphemeral(true);  //是否临时实例
        instance.setClusterName("c1");
        instance.setIp("11.11.11.11");
        instance.setPort(80);
        naming1.registerInstance(serviceName, TEST_GROUP_1, instance);
        TimeUnit.SECONDS.sleep(3L);
        naming1.deregisterInstance(serviceName, TEST_GROUP_1, instance);
        namingServiceDelete(serviceName, TEST_NAMESPACE_1, TEST_GROUP_1);
    }
    
    /**
     * @TCDescription : 根据serviceName创建服务, 通过registerInstance接口注册实例, ephemeral为false
     * @TestStep :
     * @ExpectResult :
     */
    @Test
    void registerInstanceEphemeralFalse() throws Exception {
        String serviceName = NamingBase.randomDomainName();
        namingServiceCreate(serviceName, TEST_NAMESPACE_1, TEST_GROUP_1);
        
        Instance instance = new Instance();
        instance.setEphemeral(false);  //是否临时实例
        instance.setClusterName("c1");
        instance.setIp("11.11.11.11");
        instance.setPort(80);
        naming1.registerInstance(serviceName, TEST_GROUP_1, instance);
        TimeUnit.SECONDS.sleep(3L);
        naming1.deregisterInstance(serviceName, TEST_GROUP_1, instance);
        namingServiceDelete(serviceName, TEST_NAMESPACE_1, TEST_GROUP_1);
    }
    
    /**
     * @TCDescription : 根据serviceName创建服务, 通过registerInstance接口注册实例, ephemeral为false
     * @TestStep :
     * @ExpectResult :
     */
    @Test
    void registerInstanceEphemeralFalseDeregisterInstance() throws Exception {
        String serviceName = NamingBase.randomDomainName();
        namingServiceCreate(serviceName, TEST_NAMESPACE_1, TEST_GROUP_1);
        
        Instance instance = new Instance();
        instance.setEphemeral(false);  //是否临时实例
        instance.setClusterName("c1");
        instance.setIp("11.11.11.11");
        instance.setPort(80);
        naming1.registerInstance(serviceName, TEST_GROUP_1, instance);
        naming1.deregisterInstance(serviceName, TEST_GROUP_1, instance);
        TimeUnit.SECONDS.sleep(3L);
        
        namingServiceDelete(serviceName, TEST_NAMESPACE_1, TEST_GROUP_1);
    }
    
    /**
     * @TCDescription : 根据serviceName创建服务
     * @TestStep :
     * @ExpectResult :
     */
    @Test
    void createService() throws Exception {
        String serviceName = NamingBase.randomDomainName();
        namingServiceCreate(serviceName, TEST_NAMESPACE_1);
        
        namingServiceDelete(serviceName, TEST_NAMESPACE_1);
    }
    
    /**
     * @TCDescription : 根据serviceName创建服务, 存在实例不能被删除, 抛异常
     * @TestStep :
     * @ExpectResult :
     */
    @Test
    void deleteServiceHasInstance() {
        String serviceName = NamingBase.randomDomainName();
        
        ResponseEntity<String> registerResponse = request(NamingBase.NAMING_CONTROLLER_PATH + "/instance",
                Params.newParams().appendParam("serviceName", serviceName).appendParam("ip", "11.11.11.11")
                        .appendParam("port", "80").appendParam("namespaceId", TEST_NAMESPACE_1).done(), String.class,
                HttpMethod.POST);
        assertTrue(registerResponse.getStatusCode().is2xxSuccessful());
        
        ResponseEntity<String> deleteServiceResponse = request(NamingBase.NAMING_CONTROLLER_PATH + "/service",
                Params.newParams().appendParam("serviceName", serviceName).appendParam("namespaceId", TEST_NAMESPACE_1)
                        .done(), String.class, HttpMethod.DELETE);
        assertTrue(deleteServiceResponse.getStatusCode().is4xxClientError());
    }
    
    /**
     * @TCDescription : 根据serviceName修改服务，并通过HTTP接口获取服务信息
     * @TestStep :
     * @ExpectResult :
     */
    @Test
    void getService() throws Exception {
        String serviceName = NamingBase.randomDomainName();
        namingServiceCreate(serviceName, TEST_NAMESPACE_1);
        
        ResponseEntity<String> response = request(NamingBase.NAMING_CONTROLLER_PATH + "/service",
                Params.newParams().appendParam("serviceName", serviceName).appendParam("namespaceId", TEST_NAMESPACE_1)
                        .appendParam("protectThreshold", "0.5").done(), String.class, HttpMethod.PUT);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertEquals("ok", response.getBody());
        
        //get service
        response = request(NamingBase.NAMING_CONTROLLER_PATH + "/service",
                Params.newParams().appendParam("serviceName", serviceName).appendParam("namespaceId", TEST_NAMESPACE_1)
                        .done(), String.class);
        
        assertTrue(response.getStatusCode().is2xxSuccessful());
        
        JsonNode json = JacksonUtils.toObj(response.getBody());
        assertEquals(serviceName, json.get("name").textValue());
        assertEquals("0.5", json.get("protectThreshold").asText());
        
        namingServiceDelete(serviceName, TEST_NAMESPACE_1);
    }
    
    /**
     * @TCDescription : 根据serviceName修改服务，并通过接口获取服务信息
     * @TestStep :
     * @ExpectResult :
     */
    @Test
    void getService1() throws Exception {
        String serviceName = NamingBase.randomDomainName();
        ListView<String> listView = naming1.getServicesOfServer(1, 50);
        
        namingServiceCreate(serviceName, TEST_NAMESPACE_1);
        TimeUnit.SECONDS.sleep(5L);
        
        ListView<String> listView1 = naming1.getServicesOfServer(1, 50);
        assertEquals(listView.getCount() + 1, listView1.getCount());
        
        namingServiceDelete(serviceName, TEST_NAMESPACE_1);
    }
    
    /**
     * @TCDescription : 获取服务list信息
     * @TestStep :
     * @ExpectResult :
     */
    @Test
    void listService() throws Exception {
        String serviceName = NamingBase.randomDomainName();
        ListView<String> listView = naming.getServicesOfServer(1, 50);
        namingServiceCreate(serviceName, Constants.DEFAULT_NAMESPACE_ID);
        
        //get service
        ResponseEntity<String> response = request(NamingBase.NAMING_CONTROLLER_PATH + "/service/list",
                Params.newParams().appendParam("serviceName", serviceName).appendParam("pageNo", "1")
                        .appendParam("pageSize", "150").done(), String.class);
        
        System.out.println("json = " + response.getBody());
        assertTrue(response.getStatusCode().is2xxSuccessful());
        JsonNode json = JacksonUtils.toObj(response.getBody());
        int count = json.get("count").intValue();
        assertEquals(listView.getCount() + 1, count);
        
        namingServiceDelete(serviceName, Constants.DEFAULT_NAMESPACE_ID);
    }
    
    /**
     * @TCDescription : 根据serviceName创建服务，注册持久化实例, 注销实例，删除服务
     * @TestStep :
     * @ExpectResult :
     */
    @Test
    void registerInstanceApi() throws Exception {
        String serviceName = NamingBase.randomDomainName();
        namingServiceCreate(serviceName, Constants.DEFAULT_NAMESPACE_ID);
        
        instanceRegister(serviceName, Constants.DEFAULT_NAMESPACE_ID, "33.33.33.33", TEST_PORT2_4_DOM_1);
        
        ResponseEntity<String> response = request(NAMING_CONTROLLER_PATH + "/instance/list",
                Params.newParams().appendParam("serviceName", serviceName) //获取naming中的实例
                        .appendParam("namespaceId", Constants.DEFAULT_NAMESPACE_ID).done(), String.class);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        JsonNode json = JacksonUtils.toObj(response.getBody());
        assertEquals(1, json.get("hosts").size());
        
        instanceDeregister(serviceName, Constants.DEFAULT_NAMESPACE_ID, "33.33.33.33", TEST_PORT2_4_DOM_1);
        
        namingServiceDelete(serviceName, Constants.DEFAULT_NAMESPACE_ID);
    }
    
    /**
     * @TCDescription : 根据serviceName创建服务，注册持久化实例, 查询实例，注销实例，删除服务
     * @TestStep :
     * @ExpectResult :
     */
    @Test
    void registerInstanceQuery() throws Exception {
        String serviceName = NamingBase.randomDomainName();
        namingServiceCreate(serviceName, Constants.DEFAULT_NAMESPACE_ID);
        
        instanceRegister(serviceName, Constants.DEFAULT_NAMESPACE_ID, "33.33.33.33", TEST_PORT2_4_DOM_1);
        
        List<Instance> instances = naming.getAllInstances(serviceName);
        assertEquals(1, instances.size());
        assertEquals("33.33.33.33", instances.get(0).getIp());
        
        instanceDeregister(serviceName, Constants.DEFAULT_NAMESPACE_ID, "33.33.33.33", TEST_PORT2_4_DOM_1);
        
        TimeUnit.SECONDS.sleep(3L);
        instances = naming.getAllInstances(serviceName);
        assertEquals(0, instances.size());
        
        namingServiceDelete(serviceName, Constants.DEFAULT_NAMESPACE_ID);
    }
    
    /**
     * @TCDescription : 根据serviceName创建服务，注册不同group的2个非持久化实例, 注销实例，删除服务
     * @TestStep :
     * @ExpectResult :
     */
    @Test
    void registerInstance2() throws Exception {
        String serviceName = NamingBase.randomDomainName();
        namingServiceCreate(serviceName, Constants.DEFAULT_NAMESPACE_ID);
        namingServiceCreate(serviceName, Constants.DEFAULT_NAMESPACE_ID, TEST_GROUP_1);
        
        instanceRegister(serviceName, Constants.DEFAULT_NAMESPACE_ID, "33.33.33.33", TEST_PORT2_4_DOM_1);
        instanceRegister(serviceName, Constants.DEFAULT_NAMESPACE_ID, TEST_GROUP_1, "22.22.22.22", TEST_PORT2_4_DOM_1);
        
        ResponseEntity<String> response = request(NAMING_CONTROLLER_PATH + "/instance/list",
                Params.newParams().appendParam("serviceName", serviceName) //获取naming中的实例
                        .appendParam("namespaceId", Constants.DEFAULT_NAMESPACE_ID).done(), String.class);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        JsonNode json = JacksonUtils.toObj(response.getBody());
        assertEquals(1, json.get("hosts").size());
        
        instanceDeregister(serviceName, Constants.DEFAULT_NAMESPACE_ID, "33.33.33.33", TEST_PORT2_4_DOM_1);
        instanceDeregister(serviceName, Constants.DEFAULT_NAMESPACE_ID, TEST_GROUP_1, "22.22.22.22",
                TEST_PORT2_4_DOM_1);
        
        namingServiceDelete(serviceName, Constants.DEFAULT_NAMESPACE_ID);
        namingServiceDelete(serviceName, Constants.DEFAULT_NAMESPACE_ID, TEST_GROUP_1);
    }
    
    private void instanceDeregister(String serviceName, String namespace, String ip, String port) {
        instanceDeregister(serviceName, namespace, Constants.DEFAULT_GROUP, ip, port);
    }
    
    private void instanceDeregister(String serviceName, String namespace, String groupName, String ip, String port) {
        ResponseEntity<String> response = request(NamingBase.NAMING_CONTROLLER_PATH + "/instance",
                Params.newParams().appendParam("serviceName", serviceName).appendParam("ip", ip)
                        .appendParam("port", port).appendParam("namespaceId", namespace)
                        .appendParam("groupName", groupName).appendParam("ephemeral", "false").done(), String.class,
                HttpMethod.DELETE);
        assertTrue(response.getStatusCode().is2xxSuccessful());
    }
    
    private void instanceRegister(String serviceName, String namespace, String groupName, String ip, String port) {
        ResponseEntity<String> response = request(NamingBase.NAMING_CONTROLLER_PATH + "/instance",
                Params.newParams().appendParam("serviceName", serviceName).appendParam("ip", ip)
                        .appendParam("port", port).appendParam("namespaceId", namespace)
                        .appendParam("groupName", groupName).appendParam("ephemeral", "false").done(), String.class,
                HttpMethod.POST);
        assertTrue(response.getStatusCode().is2xxSuccessful());
    }
    
    private void instanceRegister(String serviceName, String namespace, String ip, String port) {
        instanceRegister(serviceName, namespace, Constants.DEFAULT_GROUP, ip, port);
    }
    
    private void namingServiceCreate(String serviceName, String namespace) {
        namingServiceCreate(serviceName, namespace, Constants.DEFAULT_GROUP);
    }
    
    private void namingServiceCreate(String serviceName, String namespace, String groupName) {
        ResponseEntity<String> response = request(NamingBase.NAMING_CONTROLLER_PATH + "/service",
                Params.newParams().appendParam("serviceName", serviceName).appendParam("protectThreshold", "0.3")
                        .appendParam("namespaceId", namespace).appendParam("groupName", groupName).done(), String.class,
                HttpMethod.POST);
        System.out.println(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertEquals("ok", response.getBody());
    }
    
    private void namingServiceDelete(String serviceName, String namespace) {
        namingServiceDelete(serviceName, namespace, Constants.DEFAULT_GROUP);
    }
    
    private void namingServiceDelete(String serviceName, String namespace, String groupName) {
        //delete service
        ResponseEntity<String> response = request(NamingBase.NAMING_CONTROLLER_PATH + "/service",
                Params.newParams().appendParam("serviceName", serviceName).appendParam("namespaceId", namespace)
                        .appendParam("groupName", groupName).done(), String.class, HttpMethod.DELETE);
        System.out.println(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertEquals("ok", response.getBody());
    }
}
