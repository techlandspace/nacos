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

package com.alibaba.nacos.config.server.utils;

import com.alibaba.nacos.config.server.model.ConfigListenState;
import com.alibaba.nacos.config.server.service.ConfigCacheService;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.CharArrayReader;
import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MD5UtilTest {
    
    MockedStatic<ConfigCacheService> configCacheServiceMockedStatic;
    
    MockedStatic<Md5ComparatorDelegate> md5ComparatorDelegateMockedStatic;
    
    @BeforeEach
    void setUp() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("nacos.config.cache.type", "nacos");
        EnvUtil.setEnvironment(environment);
        configCacheServiceMockedStatic = Mockito.mockStatic(ConfigCacheService.class);
        md5ComparatorDelegateMockedStatic = Mockito.mockStatic(Md5ComparatorDelegate.class);
    }
    
    @AfterEach
    void tearDown() {
        configCacheServiceMockedStatic.close();
        md5ComparatorDelegateMockedStatic.close();
    }
    
    @Test
    void testCompareMd5() {
        Md5ComparatorDelegate md5ComparatorDelegate = Mockito.mock(Md5ComparatorDelegate.class);
        when(Md5ComparatorDelegate.getInstance()).thenReturn(md5ComparatorDelegate);
        
        when(ConfigCacheService.isUptodate(anyString(), anyString(), anyString(), anyString())).thenReturn(false);
        
        HashMap<String, ConfigListenState> clientMd5Map = new HashMap<>();
        clientMd5Map.put("test", new ConfigListenState("test"));
        
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Vipserver-Tag", "test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        
        when(md5ComparatorDelegate.compareMd5(request, response, clientMd5Map)).thenReturn(new HashMap<>());
        MD5Util.compareMd5(request, response, clientMd5Map);
        
        verify(md5ComparatorDelegate, times(1)).compareMd5(request, response, clientMd5Map);
        
    }
    
    @Test
    void testCompareMd5OldResult() {
        
        final MockedStatic<GroupKey2> groupKey2MockedStatic = Mockito.mockStatic(GroupKey2.class);
        
        HashMap<String, ConfigListenState> changedGroupKeys = new HashMap<>();
        changedGroupKeys.put("test", new ConfigListenState("testMd5"));
        
        String[] arr = new String[3];
        arr[0] = "test0";
        arr[1] = "test1";
        arr[2] = "test2";
        when(GroupKey2.parseKey(anyString())).thenReturn(arr);
        
        String actualValue = MD5Util.compareMd5OldResult(changedGroupKeys);
        
        assertEquals("test0:test1;", actualValue);
        
        groupKey2MockedStatic.close();
    }
    
    @Test
    void testCompareMd5ResultString() {
        
        final MockedStatic<GroupKey2> groupKey2MockedStatic = Mockito.mockStatic(GroupKey2.class);
        
        HashMap<String, ConfigListenState> changedGroupKeys = new HashMap<>();
        changedGroupKeys.put("test", new ConfigListenState("testMd5"));
        
        String[] arr = new String[3];
        arr[0] = "test0";
        arr[1] = "test1";
        arr[2] = "test2";
        when(GroupKey2.parseKey(anyString())).thenReturn(arr);
        
        try {
            String actualValue = MD5Util.compareMd5ResultString(changedGroupKeys);
            assertEquals("test0%02test1%02test2%01", actualValue);
        } catch (IOException e) {
            System.out.println(e.toString());
        }
        
        groupKey2MockedStatic.close();
    }
    
    @Test
    void testGetClientMd5Map() {
        
        String configKeysString =
                "test0" + MD5Util.WORD_SEPARATOR_CHAR + "test1" + MD5Util.WORD_SEPARATOR_CHAR + "test2"
                        + MD5Util.LINE_SEPARATOR_CHAR;
        
        Map<String, ConfigListenState> actualValueMap = MD5Util.getClientMd5Map(configKeysString);
        assertEquals("test2", actualValueMap.get("test0+test1+public").getMd5());
        
    }
    
    @Test
    void testGetClientMd5MapForNewProtocol() {
        String configKeysString =
                "test0" + MD5Util.WORD_SEPARATOR_CHAR + "test1" + MD5Util.WORD_SEPARATOR_CHAR + "test2"
                        + MD5Util.WORD_SEPARATOR_CHAR + "test3" + MD5Util.LINE_SEPARATOR_CHAR;
        
        Map<String, ConfigListenState> actualValueMap = MD5Util.getClientMd5Map(configKeysString);
        
        assertEquals("test2", actualValueMap.get("test0+test1+test3").getMd5());
    }
    
    @Test
    void testToStringV1() {
        
        try {
            InputStream input = IOUtils.toInputStream("test", StandardCharsets.UTF_8);
            String actualValue = MD5Util.toString(input, "UTF-8");
            assertEquals("test", actualValue);
        } catch (IOException e) {
            System.out.println(e.toString());
        }
    }
    
    @Test
    void testToStringV2() {
        
        try {
            Reader reader = new CharArrayReader("test".toCharArray());
            String actualValue = MD5Util.toString(reader);
            assertEquals("test", actualValue);
        } catch (IOException e) {
            System.out.println(e.toString());
        }
    }
    
    @Test
    void testCopy() {
        
        try {
            String content = "test";
            Reader input = new CharArrayReader("test".toCharArray());
            Writer output = new CharArrayWriter();
            long actualValue = MD5Util.copy(input, output);
            
            assertEquals(content.length(), actualValue);
            assertEquals(content, output.toString());
            
        } catch (IOException e) {
            System.out.println(e.toString());
        }
    }
    
}
