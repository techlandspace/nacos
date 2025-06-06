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

package com.alibaba.nacos.plugin.auth.impl.utils;

import com.alibaba.nacos.plugin.auth.impl.SafeBcryptPasswordEncoder;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import org.apache.commons.lang.StringUtils;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PasswordEncoderUtil test.
 *
 * @ClassName: PasswordEncoderUtilTest
 * @Author: ChenHao26
 * @Date: 2022/8/17 01:25
 */
class PasswordEncoderUtilTest {
    
    /**
     * encode test.
     */
    @Test
    void encode() {
        String str = PasswordEncoderUtil.encode("nacos");
        String str2 = PasswordEncoderUtil.encode("nacos");
        assertNotEquals(str2, str);
    }
    
    @Test
    void matches() {
        Boolean result1 = PasswordEncoderUtil.matches("nacos", "$2a$10$MK2dspqy7MKcCU63x8PoI.vTGXYxhzTmjWGJ21T.WX8thVsw0K2mO");
        assertTrue(result1);
        Boolean result2 = PasswordEncoderUtil.matches("nacos", "$2a$10$MK2dspqy7MKcCU63x8PoI.vTGXcxhzTmjWGJ21T.WX8thVsw0K2mO");
        assertFalse(result2);
        Boolean matches = PasswordEncoderUtil.matches("nacos", PasswordEncoderUtil.encode("nacos"));
        assertTrue(matches);
    }
    
    @Test
    void enforcePasswordLength() {
        String raw72Password =  StringUtils.repeat("A", AuthConstants.MAX_PASSWORD_LENGTH);
        String encodedPassword = PasswordEncoderUtil.encode(raw72Password);
        
        assertThrows(IllegalArgumentException.class, () -> PasswordEncoderUtil.encode(null));
        
        String raw73Password = raw72Password.concat("A");
        assertThrows(IllegalArgumentException.class, () -> PasswordEncoderUtil.encode(raw73Password));
        
        assertThrows(IllegalArgumentException.class, () -> new BCryptPasswordEncoder().matches(raw73Password, encodedPassword));
        assertFalse(new SafeBcryptPasswordEncoder().matches(raw73Password, encodedPassword));
        assertFalse(PasswordEncoderUtil.matches(raw73Password, encodedPassword));
    
    }
}
