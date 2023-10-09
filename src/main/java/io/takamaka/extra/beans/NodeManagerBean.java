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

import io.takamaka.wallet.utils.KeyContexts;
import java.util.List;
import java.util.Objects;

/**
 *
 * @author Alessandro Pasi <alessandro.pasi@takamaka.io>
 */
public class NodeManagerBean {

    private List<String> hashLinkedNode;
    private String publicKey;
    private String shortKey;
    private KeyContexts.AddressType type;

    public NodeManagerBean() {
    }

    public List<String> getHashLinkedNode() {
        return hashLinkedNode;
    }

    public void setHashLinkedNode(List<String> hashLinkedNode) {
        this.hashLinkedNode = hashLinkedNode;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public KeyContexts.AddressType getType() {
        return type;
    }

    public void setType(KeyContexts.AddressType type) {
        this.type = type;
    }

    public String getShortKey() {
        return shortKey;
    }

    public void setShortKey(String shortKey) {
        this.shortKey = shortKey;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 53 * hash + Objects.hashCode(this.hashLinkedNode);
        hash = 53 * hash + Objects.hashCode(this.publicKey);
        hash = 53 * hash + Objects.hashCode(this.shortKey);
        hash = 53 * hash + Objects.hashCode(this.type);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final NodeManagerBean other = (NodeManagerBean) obj;
        if (!Objects.equals(this.publicKey, other.publicKey)) {
            return false;
        }
        if (!Objects.equals(this.shortKey, other.shortKey)) {
            return false;
        }
        if (!Objects.equals(this.hashLinkedNode, other.hashLinkedNode)) {
            return false;
        }
        if (this.type != other.type) {
            return false;
        }
        return true;
    }

}
