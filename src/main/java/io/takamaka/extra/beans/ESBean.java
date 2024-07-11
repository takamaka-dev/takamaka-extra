/*
 * Copyright 2024 AiliA SA.
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

import io.takamaka.extra.exceptions.ForwardKeyException;
import io.takamaka.extra.utils.TkmForwardKeys;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 *
 * @author giovanni.antino@takamaka.io
 */
@Slf4j
@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ESBean implements Serializable, Comparable<ESBean> {

    private static final long serialVersionUID = 1L;

    @EqualsAndHashCode.Include
    private Integer epoch;
    @EqualsAndHashCode.Include
    private Integer slot;

    @Override
    public int compareTo(ESBean o) {
        try {
            return TkmForwardKeys.getProposedKeyName(this).compareTo(TkmForwardKeys.getProposedKeyName(o));
        } catch (ForwardKeyException ex) {
            throw new RuntimeException(ex);
        }
    }

}