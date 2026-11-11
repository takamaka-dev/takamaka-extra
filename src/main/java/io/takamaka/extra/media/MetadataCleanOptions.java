/*
 * Copyright 2026 AiliA SA.
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
package io.takamaka.extra.media;

/**
 * C120 — the one switch of the metadata cleaner (operator ruling 2026-11-11: «Like Dart: one
 * switch, OFF»). There are deliberately no per-type toggles: either every attachment goes through
 * {@link MediaMetadataCleaner} or none does.
 *
 * <p>Immutable. {@link #off()} is the default everywhere.</p>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public final class MetadataCleanOptions {

    private static final MetadataCleanOptions OFF = new MetadataCleanOptions(false);
    private static final MetadataCleanOptions ON = new MetadataCleanOptions(true);

    private final boolean enabled;

    private MetadataCleanOptions(boolean enabled) {
        this.enabled = enabled;
    }

    /** The default: nothing is cleaned, every file is used as it is. */
    public static MetadataCleanOptions off() {
        return OFF;
    }

    /** Location and metadata are removed from every type a cleaner exists for. */
    public static MetadataCleanOptions on() {
        return ON;
    }

    public static MetadataCleanOptions of(boolean enabled) {
        return enabled ? ON : OFF;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String toString() {
        return "MetadataCleanOptions{enabled=" + enabled + '}';
    }
}
