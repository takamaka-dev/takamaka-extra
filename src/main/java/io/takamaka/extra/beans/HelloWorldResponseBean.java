/*
 * Copyright 2023 AiliA SA.
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
package io.takamaka.extra.beans;

import java.util.List;
import java.util.logging.Level;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 *
 * @author isacco
 */
@Slf4j
@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class HelloWorldResponseBean extends ErrorBean {
    
    private ShellRequestBean srb;
    private String msg;
    
    public HelloWorldResponseBean(ShellRequestBean srb, boolean error, String errorMessage, List<Exception> ex, Level level) {
        super(error, errorMessage, ex, level);
        this.srb = srb;
    }

    public HelloWorldResponseBean(ShellRequestBean srb, String msg, boolean error, String errorMessage, List<Exception> ex, Level level) {
        super(error, errorMessage, ex, level);
        this.srb = srb;
        this.msg = msg;
    }
    
}
