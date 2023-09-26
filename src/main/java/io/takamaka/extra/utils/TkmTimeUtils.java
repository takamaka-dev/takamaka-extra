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
package io.takamaka.extra.utils;

import io.takamaka.extra.identicon.exceptions.InvalidEpochException;
import lombok.extern.slf4j.Slf4j;

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Slf4j
public class TkmTimeUtils {

    public static final long getAbsoluteBlockNumber(int epoch, int slot, int slotPerEpoch) throws InvalidEpochException {
        if (epoch < 0) {
            throw new InvalidEpochException(epoch + " is not a valid value for epoch");
        }
        if (slot < 0 | slot >= 24000) {
            throw new InvalidEpochException(slot + " is not a valid value for slot");
        }
        return (slotPerEpoch * epoch) + slot;
    }

    public static final boolean isInRangeInclusive(int initialEpoch, int initialSlot, int endingEpoch, int endingSlot, int targetEpoch, int targetSlot, int slotPerEpoch) throws InvalidEpochException {
        return isInRange(initialEpoch, initialSlot, endingEpoch, endingSlot, targetEpoch, targetSlot, slotPerEpoch, true, true);
    }

    public static final boolean isInRange(int initialEpoch, int initialSlot, int endingEpoch, int endingSlot, int targetEpoch, int targetSlot, int slotPerEpoch, boolean leftInclusive, boolean rightInclusive) throws InvalidEpochException {
        long rangeInit = getAbsoluteBlockNumber(initialEpoch, initialSlot, slotPerEpoch);
        long rangeEnding = getAbsoluteBlockNumber(endingEpoch, endingSlot, slotPerEpoch);
        long target = getAbsoluteBlockNumber(targetEpoch, targetSlot, slotPerEpoch);

        if (leftInclusive & rightInclusive) {
            return (rangeInit >= target) & (rangeEnding <= target);
        }
        if (leftInclusive & !rightInclusive) {
            return (rangeInit >= target) & (rangeEnding < target);
        }
        if (!leftInclusive & rightInclusive) {
            return (rangeInit > target) & (rangeEnding <= target);
        } else {
            return (rangeInit > target) & (rangeEnding < target);
        }

    }

}
