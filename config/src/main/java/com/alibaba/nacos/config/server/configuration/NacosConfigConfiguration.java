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

package com.alibaba.nacos.config.server.configuration;

import com.alibaba.nacos.config.server.filter.CircuitFilter;
import com.alibaba.nacos.config.server.filter.NacosWebFilter;
import com.alibaba.nacos.core.code.ControllerMethodsCache;
import com.alibaba.nacos.core.web.NacosWebBean;
import com.alibaba.nacos.persistence.configuration.condition.ConditionDistributedEmbedStorage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

/**
 * Nacos Config {@link Configuration} includes required Spring components.
 *
 * @author <a href="mailto:mercyblitz@gmail.com">Mercy</a>
 * @since 0.2.2
 */
@Configuration
@NacosWebBean
public class NacosConfigConfiguration {
    
    private final ControllerMethodsCache methodsCache;
    
    public NacosConfigConfiguration(ControllerMethodsCache methodsCache) {
        this.methodsCache = methodsCache;
    }
    
    @PostConstruct
    public void init() {
        methodsCache.initClassMethod("com.alibaba.nacos.config.server.controller");
    }
    
    @Bean
    @ConditionalOnProperty(name = "nacos.web.charset.filter", havingValue = "nacos", matchIfMissing = true)
    public FilterRegistrationBean<NacosWebFilter> nacosWebFilterRegistration() {
        FilterRegistrationBean<NacosWebFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(nacosWebFilter());
        registration.addUrlPatterns("/v1/cs/*");
        registration.setName("nacosWebFilter");
        registration.setOrder(1);
        return registration;
    }
    
    @Bean
    public NacosWebFilter nacosWebFilter() {
        return new NacosWebFilter();
    }
    
    @Conditional(ConditionDistributedEmbedStorage.class)
    @Bean
    public FilterRegistrationBean<CircuitFilter> transferToLeaderRegistration() {
        FilterRegistrationBean<CircuitFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(transferToLeader());
        registration.addUrlPatterns("/v1/cs/*");
        registration.setName("curcuitFilter");
        registration.setOrder(6);
        return registration;
    }
    
    @Conditional(ConditionDistributedEmbedStorage.class)
    @Bean
    public CircuitFilter transferToLeader() {
        return new CircuitFilter();
    }
    
}
