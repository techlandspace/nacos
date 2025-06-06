/*
 * Copyright 1999-$toady.year Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.controller.v3;

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.model.capacity.Capacity;
import com.alibaba.nacos.config.server.model.form.UpdateCapacityForm;
import com.alibaba.nacos.config.server.service.capacity.CapacityService;
import com.alibaba.nacos.sys.env.EnvUtil;
import jakarta.servlet.ServletContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = MockServletContext.class)
@WebAppConfiguration
class CapacityControllerV3Test {
    
    @InjectMocks
    CapacityControllerV3 capacityControllerV3;
    
    private MockMvc mockMvc;
    
    @Mock
    private CapacityService capacityService;
    
    @Mock
    private ServletContext servletContext;
    
    @BeforeEach
    void setUp() {
        EnvUtil.setEnvironment(new StandardEnvironment());
        when(servletContext.getContextPath()).thenReturn("/nacos");
        ReflectionTestUtils.setField(capacityControllerV3, "capacityService", capacityService);
        mockMvc = MockMvcBuilders.standaloneSetup(capacityControllerV3).build();
    }
    
    @Test
    void testGetCapacityNormal() throws Exception {
        
        Capacity capacity = new Capacity();
        capacity.setId(1L);
        capacity.setMaxAggrCount(1);
        capacity.setMaxSize(1);
        capacity.setMaxAggrSize(1);
        capacity.setGmtCreate(new Timestamp(1));
        capacity.setGmtModified(new Timestamp(2));
        when(capacityService.getCapacityWithDefault(eq("test"), eq("test"))).thenReturn(capacity);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(Constants.CAPACITY_CONTROLLER_V3_ADMIN_PATH).param("groupName", "test")
                .param("namespaceId", "test");
        String actualValue = mockMvc.perform(builder).andReturn().getResponse().getContentAsString();
        Capacity result = JacksonUtils.toObj(JacksonUtils.toObj(actualValue).get("data").toString(), Capacity.class);
        
        assertNotNull(result);
        assertEquals(capacity.getId(), result.getId());
        assertEquals(capacity.getMaxAggrCount(), result.getMaxAggrCount());
        assertEquals(capacity.getMaxSize(), result.getMaxSize());
        assertEquals(capacity.getMaxAggrSize(), result.getMaxAggrSize());
        assertEquals(capacity.getGmtCreate(), result.getGmtCreate());
        assertEquals(capacity.getGmtModified(), result.getGmtModified());
    }
    
    @Test
    void testGetCapacityException() throws Exception {
        
        Capacity capacity = new Capacity();
        capacity.setId(1L);
        capacity.setMaxAggrCount(1);
        capacity.setMaxSize(1);
        capacity.setMaxAggrSize(1);
        capacity.setGmtCreate(new Timestamp(1));
        capacity.setGmtModified(new Timestamp(2));
        when(capacityService.getCapacityWithDefault(eq("test"), eq("test"))).thenReturn(capacity);
        // namespaceId & groupName is null
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(Constants.CAPACITY_CONTROLLER_V3_ADMIN_PATH);
        assertThrows(Exception.class, () -> {
            mockMvc.perform(builder);
        });
        
        // namespaceId is blank& groupName is null
        MockHttpServletRequestBuilder builder2 = MockMvcRequestBuilders.get(Constants.CAPACITY_CONTROLLER_V3_ADMIN_PATH).param("namespaceId", "");
        assertThrows(Exception.class, () -> {
            mockMvc.perform(builder2);
        });
        
        // namespaceId is not blank && groupName is not blank
        when(capacityService.getCapacityWithDefault(eq("g1"), eq("123"))).thenThrow(new NullPointerException());
        MockHttpServletRequestBuilder builder3 = MockMvcRequestBuilders.get(Constants.CAPACITY_CONTROLLER_V3_ADMIN_PATH).param("namespaceId", "123")
                .param("groupName", "g1");
        String actualValue3 = mockMvc.perform(builder3).andReturn().getResponse().getContentAsString();
        
    }
    
    @Test
    void testUpdateCapacity1x() throws Exception {

        when(capacityService.insertOrUpdateCapacity("test", "test", 1, 1, 1, 1)).thenReturn(true);

        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.post(Constants.CAPACITY_CONTROLLER_V3_ADMIN_PATH).param("groupName", "test")
                .param("namespaceId", "test").param("quota", "1").param("maxSize", "1").param("maxAggrCount", "1").param("maxAggrSize", "1");

        String actualValue = mockMvc.perform(builder).andReturn().getResponse().getContentAsString();
        String code = JacksonUtils.toObj(actualValue).get("code").toString();
        String data = JacksonUtils.toObj(actualValue).get("data").toString();
        assertEquals("0", code);
        assertEquals("true", data);
    }
    
    @Test
    void testUpdateCapacity4x() throws Exception {
        
        UpdateCapacityForm updateCapacityForm = new UpdateCapacityForm();
        updateCapacityForm.setGroupName("test");
        updateCapacityForm.setNamespaceId("test");
        updateCapacityForm.setQuota(1);
        updateCapacityForm.setMaxSize(1);
        updateCapacityForm.setMaxAggrCount(1);
        updateCapacityForm.setMaxSize(1);
        when(capacityService.insertOrUpdateCapacity("test", "test", 1, 1, 1, 1)).thenReturn(false);
        
        MockHttpServletRequestBuilder builder = post(Constants.CAPACITY_CONTROLLER_V3_ADMIN_PATH).param("groupName", "test")
                .param("namespaceId", "test").param("quota", "1").param("maxSize", "1").param("maxAggrCount", "1").param("maxAggrSize", "1");
        String actualValue = mockMvc.perform(builder).andReturn().getResponse().getContentAsString();
        String code = JacksonUtils.toObj(actualValue).get("code").toString();
        String data = JacksonUtils.toObj(actualValue).get("data").toString();
        assertEquals("30000", code);
        assertEquals("null", data);
    }
}
    
    