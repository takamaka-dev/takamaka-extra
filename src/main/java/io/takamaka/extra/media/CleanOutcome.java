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

import java.nio.file.Path;

/**
 * What {@link MediaMetadataCleaner#clean} did with one file. Mirror of the Dart
 * {@code MediaCleanOutcome} (tkmChat {@code lib/services/media_cleanup/media_metadata_cleaner.dart}).
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public final class CleanOutcome {

    /** How a file came out of the cleaner. */
    public enum Status {
        /** Metadata was found and removed; {@link #getPath()} is the NEW copy. */
        CLEANED,
        /** A cleanable type that carried nothing to remove; the original is used. */
        UNCHANGED,
        /**
         * No cleaner exists for this type (or a document is too large to hold in memory, or the
         * switch is off): the original is used and the CALLER must say so to the user.
         */
        PASSTHROUGH,
        /**
         * A cleanable type whose structure could not be walked, or a media type nobody can vouch
         * for (WebP, HEIC, WebM, an image above the in-memory ceiling): the caller must LEAVE THE
         * FILE OUT — never send it as if it were clean.
         */
        FAILED
    }

    private final Status status;
    private final Path path;
    private final String detail;

    public CleanOutcome(Status status, Path path, String detail) {
        this.status = status;
        this.path = path;
        this.detail = detail;
    }

    public Status getStatus() {
        return status;
    }

    /**
     * The file to use: the cleaned copy for {@link Status#CLEANED}, the source for every other
     * status. For {@link Status#FAILED} it is the source and must NOT be sent.
     */
    public Path getPath() {
        return path;
    }

    /** One line for the log / the user. */
    public String getDetail() {
        return detail;
    }

    /** False only for {@link Status#FAILED}: the send must be refused. */
    public boolean isSendable() {
        return status != Status.FAILED;
    }

    @Override
    public String toString() {
        return "CleanOutcome{" + status + ", " + path + ", " + detail + '}';
    }
}
