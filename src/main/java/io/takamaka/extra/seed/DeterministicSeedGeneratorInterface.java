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
package io.takamaka.extra.seed;

import io.takamaka.wallet.exceptions.InvalidWalletIndexException;
import io.takamaka.wallet.exceptions.WalletException;
import io.takamaka.wallet.utils.SeededRandom;

/**
 *
 * @author giovanni.antino@h2tcoin.com
 */
public interface DeterministicSeedGeneratorInterface {

    public byte[] getSeedBytes(String scope, int index, int numOfBytes) throws WalletException;

    public String getSeedB64Url(String scope, int index, int numOfBytes) throws WalletException;

    public SeededRandom getSeededRandomGeneratorAtIndex(String scope, int index) throws InvalidWalletIndexException;
}
