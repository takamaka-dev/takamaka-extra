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

import javax.management.NotificationBroadcasterSupport;

/**
 *
 * @author Alessandro Pasi <alessandro.pasi@takamaka.io>
 */
public abstract class AbstractGenericCommand extends AbstractNotifyMessage implements ExecutableRemoteCommand {

    protected String args[];
    protected NotificationBroadcasterSupport nbs;
    protected long sequence;

    public AbstractGenericCommand(String[] args, NotificationBroadcasterSupport nbs, long sequence) {
        this.args = args;
        this.nbs = nbs;
        this.sequence = sequence;
    }

}