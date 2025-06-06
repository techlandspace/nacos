/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.persistence.datasource;

import com.alibaba.nacos.persistence.configuration.DatasourceConfiguration;
import com.alibaba.nacos.persistence.constants.PersistenceConstant;
import com.alibaba.nacos.sys.env.Constants;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer.MethodName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ClusterExternalStorage unit test.
 *
 * @author Long Yu
 * @since 2.2.0
 */
@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodName.class)
class ClusterExternalStorageTest {
    
    DatasourceConfiguration datasourceConfig;
    
    @InjectMocks
    private DynamicDataSource dataSource;
    
    private MockEnvironment environment;
    
    @Mock
    private LocalDataSourceServiceImpl localDataSourceService;
    
    @Mock
    private ExternalDataSourceServiceImpl basicDataSourceService;
    
    @BeforeEach
    void setUp() throws Exception {
        environment = new MockEnvironment();
        EnvUtil.setEnvironment(environment);
        datasourceConfig = new DatasourceConfiguration();
        dataSource = DynamicDataSource.getInstance();
        ReflectionTestUtils.setField(dataSource, "localDataSourceService", localDataSourceService);
        ReflectionTestUtils.setField(dataSource, "basicDataSourceService", basicDataSourceService);
        
    }
    
    @Test
    void test005WithClusterAndNullDatabase() {
        // 模拟设置环境05：指定集群，未指定数据库，UseExternalDB是true，数据库类型是""
        System.setProperty(Constants.STANDALONE_MODE_PROPERTY_NAME, "false");
        environment.setProperty(PersistenceConstant.DATASOURCE_PLATFORM_PROPERTY_OLD, "");
        EnvUtil.setIsStandalone(Boolean.getBoolean(Constants.STANDALONE_MODE_PROPERTY_NAME));
        DatasourceConfiguration.setEmbeddedStorage(EnvUtil.getStandaloneMode());
        
        // 模拟初始化
        datasourceConfig.initialize(null);
        
        assertFalse(EnvUtil.getStandaloneMode());
        assertTrue(DatasourceConfiguration.isUseExternalDb());
        assertTrue(dataSource.getDataSource() instanceof ExternalDataSourceServiceImpl);
    }
    
    @Test
    void test006WithClusterAndMysqlDatabase() {
        // 模拟设置环境06：指定集群，指定数据库mysql，UseExternalDB是true，数据库类型是mysql
        System.setProperty(Constants.STANDALONE_MODE_PROPERTY_NAME, "false");
        environment.setProperty(PersistenceConstant.DATASOURCE_PLATFORM_PROPERTY_OLD, "mysql");
        EnvUtil.setIsStandalone(Boolean.getBoolean(Constants.STANDALONE_MODE_PROPERTY_NAME));
        DatasourceConfiguration.setEmbeddedStorage(EnvUtil.getStandaloneMode());
        
        // 模拟初始化
        datasourceConfig.initialize(null);
        
        assertFalse(EnvUtil.getStandaloneMode());
        assertTrue(DatasourceConfiguration.isUseExternalDb());
        assertTrue(dataSource.getDataSource() instanceof ExternalDataSourceServiceImpl);
    }
    
    @Test
    void test007WithClusterAndDerbyDatabase() {
        // 模拟设置环境07：指定集群，指定数据库derby，UseExternalDB是false，数据库类型是derby
        System.setProperty(Constants.STANDALONE_MODE_PROPERTY_NAME, "false");
        environment.setProperty(PersistenceConstant.DATASOURCE_PLATFORM_PROPERTY_OLD, "derby");
        EnvUtil.setIsStandalone(Boolean.getBoolean(Constants.STANDALONE_MODE_PROPERTY_NAME));
        DatasourceConfiguration.setEmbeddedStorage(true);
        
        // 模拟初始化
        datasourceConfig.initialize(null);
        
        assertFalse(EnvUtil.getStandaloneMode());
        assertFalse(DatasourceConfiguration.isUseExternalDb());
        assertTrue(dataSource.getDataSource() instanceof LocalDataSourceServiceImpl);
    }
    
    @Test
    void test008WithClusterAndOtherDatabase() {
        // 模拟设置环境08: 指定集群，指定数据库其他，UseExternalDB是true，数据库类型是其他
        System.setProperty(Constants.STANDALONE_MODE_PROPERTY_NAME, "false");
        environment.setProperty(PersistenceConstant.DATASOURCE_PLATFORM_PROPERTY_OLD, "postgresql");
        EnvUtil.setIsStandalone(Boolean.getBoolean(Constants.STANDALONE_MODE_PROPERTY_NAME));
        DatasourceConfiguration.setEmbeddedStorage(EnvUtil.getStandaloneMode());
        
        // 模拟初始化
        datasourceConfig.initialize(null);
        
        assertFalse(EnvUtil.getStandaloneMode());
        assertTrue(DatasourceConfiguration.isUseExternalDb());
        assertTrue(dataSource.getDataSource() instanceof ExternalDataSourceServiceImpl);
    }
    
}
