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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListMap;
import lombok.extern.slf4j.Slf4j;

/**
 *
 * @author Giovanni
 */
@Slf4j
public class TkmErrorUtils {

    public static final ConcurrentSkipListMap<String, Exception> getErrorMapper() {
        return new ConcurrentSkipListMap<String, Exception>();
    }

    public static final void logAllErrors(ConcurrentSkipListMap<String, Exception> errorMap) {
        errorMap.forEach((s, e) -> {
            log.error(s, e);
            String stackTrace = getStackTrace(e);
            log.error(stackTrace);
        });
    }

    public static final void appendException(String message, Exception ex, ConcurrentSkipListMap<String, Exception> errorMapper) {
        errorMapper.put(message, ex);
    }

    public static final void appendException(Exception ex, ConcurrentSkipListMap<String, Exception> errorMapper) {
        errorMapper.put(ex.getMessage() + "- rnd uid: " + UUID.randomUUID().toString(), ex);
    }

    public static final String getStackTrace(final Throwable throwable) {
        final StringWriter sw = new StringWriter();
        final PrintWriter pw = new PrintWriter(sw, true);
        throwable.printStackTrace(pw);
        return sw.getBuffer().toString();
    }

    public static final <T extends Exception> void logExceptionAndContinue(T e) {
        log.error(e.getMessage(), e);
    }

}
