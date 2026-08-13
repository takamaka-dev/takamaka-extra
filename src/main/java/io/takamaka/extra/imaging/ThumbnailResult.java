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
package io.takamaka.extra.imaging;

/**
 * The outcome of {@link ThumbnailHelper#generatePreview}.
 *
 * <p>Deliberately NOT a boolean and NOT a nullable byte array. There are five distinct outcomes
 * and three of them mean "no preview" for entirely different reasons — "the content is small
 * enough to travel inline" is a success, "this is not an image" is a shrug, and "the thumbnail
 * would not fit" is a fact the caller is required to LOG. Collapsing them to {@code null} is how
 * a dropped preview becomes indistinguishable from a working one, which is the defect class
 * §PREVIEW-CONFORMANCE exists to close.</p>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 * @since 0.6.0
 */
public final class ThumbnailResult {

    /**
     * Why there is (or is not) a preview.
     */
    public enum Outcome {
        /** A preview was generated. {@link #getPreviewBytes()} is non-null. */
        PREVIEW,
        /**
         * The source fits within the inline byte limit, so it should be SENT INLINE and no preview
         * generated — a preview of content the receiver already holds is pure overhead
         * (§PREVIEW-SPEC rule 1). Not an error.
         */
        INLINE,
        /** The bytes could not be decoded as an image, or exceed the pixel guard. No preview. */
        NOT_AN_IMAGE,
        /**
         * A preview was generated but exceeded the byte limit, so it was DROPPED. The caller MUST
         * log this: it is the one outcome where the user silently gets less than they asked for.
         */
        TOO_LARGE,
        /** Nothing to work with. */
        EMPTY_SOURCE
    }

    private final Outcome outcome;
    private final byte[] previewBytes;
    private final String previewMediaType;
    private final int previewWidth;
    private final int previewHeight;
    private final int sourceWidth;
    private final int sourceHeight;
    private final int exifOrientation;
    private final String reason;

    ThumbnailResult(Outcome outcome, byte[] previewBytes, String previewMediaType,
            int previewWidth, int previewHeight, int sourceWidth, int sourceHeight,
            int exifOrientation, String reason) {
        this.outcome = outcome;
        this.previewBytes = previewBytes;
        this.previewMediaType = previewMediaType;
        this.previewWidth = previewWidth;
        this.previewHeight = previewHeight;
        this.sourceWidth = sourceWidth;
        this.sourceHeight = sourceHeight;
        this.exifOrientation = exifOrientation;
        this.reason = reason;
    }

    static ThumbnailResult of(Outcome outcome, String reason) {
        return new ThumbnailResult(outcome, null, null, 0, 0, 0, 0,
                ExifOrientation.NORMAL, reason);
    }

    /** @return which of the five outcomes this is; never {@code null} */
    public Outcome getOutcome() {
        return outcome;
    }

    /** @return {@code true} iff a preview was produced */
    public boolean hasPreview() {
        return outcome == Outcome.PREVIEW && previewBytes != null;
    }

    /**
     * @return {@code true} iff the source should be delivered inline instead
     *         ({@code is_the_object = true}), with no preview at all
     */
    public boolean shouldInline() {
        return outcome == Outcome.INLINE;
    }

    /** @return the encoded preview, or {@code null} unless {@link #hasPreview()} */
    public byte[] getPreviewBytes() {
        return previewBytes;
    }

    /**
     * The preview's OWN type — {@code image/jpeg} or {@code image/png}.
     *
     * <p>⚠️ This is <strong>not</strong> what goes in the placeholder's {@code media_type}, which
     * always describes the ORIGINAL OBJECT. There is no wire field for this value by design;
     * consumers identify a preview by magic bytes. It is returned for logging and for the
     * producible-type check.</p>
     *
     * @return the preview's media type, or {@code null} unless {@link #hasPreview()}
     */
    public String getPreviewMediaType() {
        return previewMediaType;
    }

    /** @return preview width in px, or 0 */
    public int getPreviewWidth() {
        return previewWidth;
    }

    /** @return preview height in px, or 0 */
    public int getPreviewHeight() {
        return previewHeight;
    }

    /** @return the source's DISPLAYED width, i.e. after EXIF orientation, or 0 */
    public int getSourceWidth() {
        return sourceWidth;
    }

    /** @return the source's DISPLAYED height, i.e. after EXIF orientation, or 0 */
    public int getSourceHeight() {
        return sourceHeight;
    }

    /** @return the orientation actually applied, 1..8 ({@code 1} when absent or malformed) */
    public int getExifOrientation() {
        return exifOrientation;
    }

    /** @return a human-readable explanation, suitable for the log line a drop requires */
    public String getReason() {
        return reason;
    }

    @Override
    public String toString() {
        return "ThumbnailResult{" + outcome
                + (hasPreview()
                        ? ", " + previewMediaType + " " + previewWidth + "x" + previewHeight
                        + " " + previewBytes.length + "B"
                        : "")
                + ", source=" + sourceWidth + "x" + sourceHeight
                + ", exif=" + exifOrientation
                + ", reason=" + reason + '}';
    }
}
