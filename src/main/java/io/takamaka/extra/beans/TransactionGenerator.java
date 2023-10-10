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

import io.takamaka.wallet.InstanceWalletKeyStoreBCED25519;
import io.takamaka.wallet.InstanceWalletKeyStoreBCQTESLAPSSC1Round1;
import io.takamaka.wallet.InstanceWalletKeyStoreBCQTESLAPSSC1Round2;
import io.takamaka.wallet.InstanceWalletKeystoreInterface;
import io.takamaka.wallet.beans.InternalTransactionBean;
import io.takamaka.wallet.beans.TransactionBean;
import io.takamaka.wallet.beans.TransactionBox;
import io.takamaka.wallet.exceptions.HashCompositionException;
import io.takamaka.wallet.exceptions.InvalidWalletIndexException;
import io.takamaka.wallet.exceptions.NullInternalTransactionBeanException;
import io.takamaka.wallet.exceptions.PublicKeySerializzationException;
import io.takamaka.wallet.exceptions.TransactionCanNotBeCreatedException;
import io.takamaka.wallet.exceptions.TransactionNotYetImplementedException;
import io.takamaka.wallet.exceptions.UnlockWalletException;
import io.takamaka.wallet.exceptions.WalletBurnedException;
import io.takamaka.wallet.exceptions.WalletEmptySeedException;
import io.takamaka.wallet.exceptions.WalletException;
import io.takamaka.wallet.utils.BuilderITB;
import static io.takamaka.wallet.utils.BuilderITB.getInternalTransactionBean;
import io.takamaka.wallet.utils.KeyContexts;
import io.takamaka.wallet.utils.TkmTK;
import io.takamaka.wallet.utils.TkmTextUtils;
import io.takamaka.wallet.utils.TkmWallet;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Random;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;

/**
 *
 * @author Alessandro Pasi <alessandro.pasi@takamaka.io>
 */
@Slf4j
class TransactionGenerator {

    public static TransactionBox[] fromJsonToTbox(String[] transactionArray) {
        Date[] dd = new Date[2];
        //String transactionArray[];
        TransactionBox tbA[] = new TransactionBox[transactionArray.length];
        String timeMessage;
        timeMessage = "json decode";
        System.out.println(timeMessage);
        dd[0] = new Date();
        int j = 0;
        /*
        for (int i = 0; i < transactionArray.length; i++) {
            if (Integer.remainderUnsigned(i, 10000) == 0) {
                System.out.println("verifing key number\t" + i + "\t\tblock number\t\t" + i / 10000);
            }
            tbA[i] = TkmWallet.verifyTransactionIntegrity(transactionArray[i]);

            if (!tbA[i].isValid()) {
                j++;
            }
        }*/

        IntStream.range(0, transactionArray.length).parallel().forEach(i -> {
            tbA[i] = TkmWallet.verifyTransactionIntegrity(transactionArray[i]);
        });
        dd[1] = new Date();
        System.out.println(timeMessage + " time: " + (dd[1].getTime() - dd[0].getTime()));
        return tbA;
    }

    public static String[] generateTransactionArray(int numberOfTransaction, KeyContexts.TransactionType type, String walletPrefix, int numberOfFromWallets, int numberOfToWallets, int fromWalletSpace, int toWalletSpace) throws TransactionCanNotBeCreatedException, TransactionNotYetImplementedException, InvalidWalletIndexException, PublicKeySerializzationException, UnlockWalletException {
        String[] transactions = new String[numberOfTransaction];
        System.out.println("number of transactions: " + transactions.length);
        InstanceWalletKeyStoreBCED25519[] fromKeys = new InstanceWalletKeyStoreBCED25519[numberOfFromWallets];
        InstanceWalletKeyStoreBCED25519[] toKeys = new InstanceWalletKeyStoreBCED25519[numberOfToWallets];
        System.out.println("From keyspace: " + numberOfFromWallets * fromWalletSpace);
        System.out.println("To keyspace: " + numberOfFromWallets * fromWalletSpace);
        for (int i = 0; i < fromKeys.length; i++) {
            fromKeys[i] = new InstanceWalletKeyStoreBCED25519(walletPrefix + "_tkm_test_factory_demo_from_wallet_" + i);
        }
        for (int i = 0; i < toKeys.length; i++) {
            toKeys[i] = new InstanceWalletKeyStoreBCED25519(walletPrefix + "_tkm_test_factory_demo_to_wallet_" + i);
        }
        System.out.print("Progress: 0%");
        int indPrg = numberOfTransaction / 20;
        boolean firstRun = false;
        for (int i = 0; i < numberOfTransaction; i++) {
            if (i % indPrg == 0) {
                if (firstRun) {
                    float val = i;
                    System.out.print("..." + (int) (val / numberOfTransaction * 100) + "%");
                } else {
                    firstRun = true;
                }
            }

            int fromWallet = pickANumberInRange(0, numberOfFromWallets);
            int toWallet = pickANumberInRange(0, numberOfToWallets);
            int fromKey = pickANumberInRange(0, fromWalletSpace);
            int toKey = pickANumberInRange(0, toWalletSpace);

            transactions[i] = TkmTextUtils.toJson(
                    TkmWallet.createGenericTransaction(
                            getInternalTransactionBean(
                                    type,
                                    fromKeys[fromWallet].getPublicKeyAtIndexURL64(fromKey),
                                    toKeys[toWallet].getPublicKeyAtIndexURL64(toKey),
                                    pickANumberAsCoin(10),
                                    pickANumberAsCoin(10),
                                    createIdentifingMessage(walletPrefix, fromWallet, fromKey, toWallet, toKey)
                            ),
                            fromKeys[fromWallet],
                            fromKey)
            );

        }
        System.out.println("...100%");
        return transactions;
    }

    public static String[] generateAssignOverflowTransactionArray() throws TransactionCanNotBeCreatedException, TransactionNotYetImplementedException, InvalidWalletIndexException, PublicKeySerializzationException, WalletException {
        String[] transactions;
        ArrayList<String> transactionList = new ArrayList<>();

        InstanceWalletKeyStoreBCED25519 alice = new InstanceWalletKeyStoreBCED25519("a");
        InstanceWalletKeyStoreBCED25519 bob = new InstanceWalletKeyStoreBCED25519("b");
        InstanceWalletKeyStoreBCED25519 charlie = new InstanceWalletKeyStoreBCED25519("c");
        InstanceWalletKeyStoreBCED25519 delta = new InstanceWalletKeyStoreBCED25519("d");
        InstanceWalletKeyStoreBCED25519 echo = new InstanceWalletKeyStoreBCED25519("e");
        InstanceWalletKeyStoreBCED25519 fox = new InstanceWalletKeyStoreBCED25519("f");

        InstanceWalletKeyStoreBCED25519 p1 = new InstanceWalletKeyStoreBCED25519("p1");
        InstanceWalletKeyStoreBCED25519 p2 = new InstanceWalletKeyStoreBCED25519("p2");
        InstanceWalletKeyStoreBCED25519 p3 = new InstanceWalletKeyStoreBCED25519("p3");
        InstanceWalletKeyStoreBCED25519 p4 = new InstanceWalletKeyStoreBCED25519("p4");
        InstanceWalletKeyStoreBCED25519 p5 = new InstanceWalletKeyStoreBCED25519("p5");
        InstanceWalletKeyStoreBCED25519 p6 = new InstanceWalletKeyStoreBCED25519("p6");

        System.out.println("a - " + alice.getPublicKeyAtIndexURL64(0));
        System.out.println("b - " + bob.getPublicKeyAtIndexURL64(0));
        System.out.println("c - " + charlie.getPublicKeyAtIndexURL64(0));
        System.out.println("d - " + delta.getPublicKeyAtIndexURL64(0));
        System.out.println("e - " + echo.getPublicKeyAtIndexURL64(0));
        System.out.println("f - " + fox.getPublicKeyAtIndexURL64(0));

        System.out.println("p1 - " + p1.getPublicKeyAtIndexURL64(0));
        System.out.println("p2 - " + p2.getPublicKeyAtIndexURL64(0));
        System.out.println("p3 - " + p3.getPublicKeyAtIndexURL64(0));
        System.out.println("p4 - " + p4.getPublicKeyAtIndexURL64(0));
        System.out.println("p5 - " + p5.getPublicKeyAtIndexURL64(0));
        System.out.println("p6 - " + p6.getPublicKeyAtIndexURL64(0));

        //Create declaration
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.DECLARATION, alice, alice, 0, 0, new BigInteger("100000000000000000"), new BigInteger("10000000000000"), "declaration a"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.DECLARATION, bob, bob, 0, 0, new BigInteger("100000000000000000"), new BigInteger("10000000000000"), "declaration b"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.DECLARATION, charlie, charlie, 0, 0, new BigInteger("100000000000000000"), new BigInteger("10000000000000"), "declaration c"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.DECLARATION, delta, delta, 0, 0, new BigInteger("100000000000000000"), new BigInteger("10000000000000"), "declaration d"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.DECLARATION, echo, echo, 0, 0, new BigInteger("100000000000000000"), new BigInteger("10000000000000"), "declaration e"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.DECLARATION, fox, fox, 0, 0, new BigInteger("100000000000000000"), new BigInteger("10000000000000"), "declaration f"));

        //register main
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.REGISTER_MAIN, alice, alice, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "REGISTER_MAIN a"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.REGISTER_MAIN, bob, bob, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "REGISTER_MAIN b"));
        //register overflow
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.REGISTER_OVERFLOW, charlie, charlie, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "REGISTER_OVERFLOW c"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.REGISTER_OVERFLOW, delta, delta, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "REGISTER_OVERFLOW d"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.REGISTER_OVERFLOW, echo, echo, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "REGISTER_OVERFLOW e"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.REGISTER_OVERFLOW, fox, fox, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "REGISTER_OVERFLOW f"));
        //assign overflow
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.ASSIGN_OVERFLOW, alice, charlie, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "ASSIGN_OVERFLOW a c"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.ASSIGN_OVERFLOW, bob, delta, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "ASSIGN_OVERFLOW b d"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.ASSIGN_OVERFLOW, bob, echo, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "ASSIGN_OVERFLOW b e"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.ASSIGN_OVERFLOW, bob, fox, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "ASSIGN_OVERFLOW b f"));
        //stake
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.STAKE, charlie, alice, 0, 0, TkmTK.unitTK(2000), new BigInteger("10000000000000"), "ASSIGN_OVERFLOW a c"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.STAKE, charlie, bob, 0, 0, TkmTK.unitTK(500), new BigInteger("10000000000000"), "ASSIGN_OVERFLOW c b"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.STAKE, delta, bob, 0, 0, TkmTK.unitTK(3000), new BigInteger("10000000000000"), "ASSIGN_OVERFLOW b d"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.STAKE, echo, charlie, 0, 0, TkmTK.unitTK(1000), new BigInteger("10000000000000"), "ASSIGN_OVERFLOW e c"));
        //transactionList.add(getTransactionJson(KeyContexts.TransactionType.STAKE, fox, bob, 0, 0, TkmTK.unitTK(3000), new BigInteger("10000000000000"), "ASSIGN_OVERFLOW b f"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.STAKE, bob, echo, 0, 0, TkmTK.unitTK(400), new BigInteger("10000000000000"), "ASSIGN_OVERFLOW b d"));

        transactions = new String[transactionList.size()];
        return transactionList.toArray(transactions);
    }
    
    public static String[] generateDeregisterOverflowTransactionArray() throws TransactionCanNotBeCreatedException, TransactionNotYetImplementedException, InvalidWalletIndexException, PublicKeySerializzationException, WalletException {
        String[] transactions;
        ArrayList<String> transactionList = new ArrayList<>();
        /*
        InstanceWalletKeyStoreBCED25519 alice = new InstanceWalletKeyStoreBCED25519("a");
        InstanceWalletKeyStoreBCED25519 bob = new InstanceWalletKeyStoreBCED25519("b");
        InstanceWalletKeyStoreBCED25519 charlie = new InstanceWalletKeyStoreBCED25519("c");
        InstanceWalletKeyStoreBCED25519 delta = new InstanceWalletKeyStoreBCED25519("d");
        InstanceWalletKeyStoreBCED25519 echo = new InstanceWalletKeyStoreBCED25519("e");
         */
        InstanceWalletKeyStoreBCED25519 fox = new InstanceWalletKeyStoreBCED25519("f");
        InstanceWalletKeyStoreBCED25519 delta = new InstanceWalletKeyStoreBCED25519("d");
        /*
        System.out.println("a - " + alice.getPublicKeyAtIndexURL64(0));
        System.out.println("b - " + bob.getPublicKeyAtIndexURL64(0));
        System.out.println("c - " + charlie.getPublicKeyAtIndexURL64(0));
        System.out.println("d - " + delta.getPublicKeyAtIndexURL64(0));
        System.out.println("e - " + echo.getPublicKeyAtIndexURL64(0));
        System.out.println("f - " + fox.getPublicKeyAtIndexURL64(0));
        

        //Create declaration
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.DECLARATION, alice, alice, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "declaration a"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.DECLARATION, bob, bob, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "declaration b"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.DECLARATION, charlie, charlie, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "declaration c"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.DECLARATION, delta, delta, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "declaration d"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.DECLARATION, echo, echo, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "declaration e"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.DECLARATION, fox, fox, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "declaration f"));

        //register main
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.REGISTER_MAIN, alice, alice, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "REGISTER_MAIN a"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.REGISTER_MAIN, bob, bob, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "REGISTER_MAIN b"));
        //register overflow
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.REGISTER_OVERFLOW, charlie, charlie, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "REGISTER_OVERFLOW c"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.REGISTER_OVERFLOW, delta, delta, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "REGISTER_OVERFLOW d"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.REGISTER_OVERFLOW, echo, echo, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "REGISTER_OVERFLOW e"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.REGISTER_OVERFLOW, fox, fox, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "REGISTER_OVERFLOW f"));
        //assign overflow
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.ASSIGN_OVERFLOW, alice, charlie, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "ASSIGN_OVERFLOW a c"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.ASSIGN_OVERFLOW, bob, delta, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "ASSIGN_OVERFLOW b d"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.ASSIGN_OVERFLOW, bob, echo, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "ASSIGN_OVERFLOW b e"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.ASSIGN_OVERFLOW, bob, fox, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "ASSIGN_OVERFLOW b f"));
         */
        //deregister overflow
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.DEREGISTER_OVERFLOW, fox, fox, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "DEREGISTER_OVERFLOW f"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.DEREGISTER_OVERFLOW, delta, delta, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "DEREGISTER_OVERFLOW d"));

        transactions = new String[transactionList.size()];
        return transactionList.toArray(transactions);
    }

    public static String[] generateDeregisterOverflowTransactionArray_V2() throws TransactionCanNotBeCreatedException, TransactionNotYetImplementedException, InvalidWalletIndexException, PublicKeySerializzationException, WalletException {
        String[] transactions;
        ArrayList<String> transactionList = new ArrayList<>();
        /*
        InstanceWalletKeyStoreBCED25519 alice = new InstanceWalletKeyStoreBCED25519("a");
        InstanceWalletKeyStoreBCED25519 bob = new InstanceWalletKeyStoreBCED25519("b");
        InstanceWalletKeyStoreBCED25519 charlie = new InstanceWalletKeyStoreBCED25519("c");
        InstanceWalletKeyStoreBCED25519 delta = new InstanceWalletKeyStoreBCED25519("d");
        InstanceWalletKeyStoreBCED25519 echo = new InstanceWalletKeyStoreBCED25519("e");
         */
        InstanceWalletKeyStoreBCED25519 c = new InstanceWalletKeyStoreBCED25519("c");
        InstanceWalletKeyStoreBCED25519 e = new InstanceWalletKeyStoreBCED25519("e");
        /*
        System.out.println("a - " + alice.getPublicKeyAtIndexURL64(0));
        System.out.println("b - " + bob.getPublicKeyAtIndexURL64(0));
        System.out.println("c - " + charlie.getPublicKeyAtIndexURL64(0));
        System.out.println("d - " + delta.getPublicKeyAtIndexURL64(0));
        System.out.println("e - " + echo.getPublicKeyAtIndexURL64(0));
        System.out.println("f - " + fox.getPublicKeyAtIndexURL64(0));
        

        //Create declaration
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.DECLARATION, alice, alice, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "declaration a"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.DECLARATION, bob, bob, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "declaration b"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.DECLARATION, charlie, charlie, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "declaration c"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.DECLARATION, delta, delta, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "declaration d"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.DECLARATION, echo, echo, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "declaration e"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.DECLARATION, fox, fox, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "declaration f"));

        //register main
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.REGISTER_MAIN, alice, alice, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "REGISTER_MAIN a"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.REGISTER_MAIN, bob, bob, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "REGISTER_MAIN b"));
        //register overflow
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.REGISTER_OVERFLOW, charlie, charlie, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "REGISTER_OVERFLOW c"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.REGISTER_OVERFLOW, delta, delta, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "REGISTER_OVERFLOW d"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.REGISTER_OVERFLOW, echo, echo, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "REGISTER_OVERFLOW e"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.REGISTER_OVERFLOW, fox, fox, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "REGISTER_OVERFLOW f"));
        //assign overflow
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.ASSIGN_OVERFLOW, alice, charlie, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "ASSIGN_OVERFLOW a c"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.ASSIGN_OVERFLOW, bob, delta, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "ASSIGN_OVERFLOW b d"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.ASSIGN_OVERFLOW, bob, echo, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "ASSIGN_OVERFLOW b e"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.ASSIGN_OVERFLOW, bob, fox, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "ASSIGN_OVERFLOW b f"));
         */
        //deregister overflow
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.DEREGISTER_OVERFLOW, c, c, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "DEREGISTER_OVERFLOW c"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.DEREGISTER_OVERFLOW, e, e, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "DEREGISTER_OVERFLOW e"));

        transactions = new String[transactionList.size()];
        return transactionList.toArray(transactions);
    }

    public static String[] generateDeregisterMainTransactionArray() throws TransactionCanNotBeCreatedException, TransactionNotYetImplementedException, InvalidWalletIndexException, PublicKeySerializzationException, WalletException {
        String[] transactions;
        ArrayList<String> transactionList = new ArrayList<>();
        /*
        InstanceWalletKeyStoreBCED25519 alice = new InstanceWalletKeyStoreBCED25519("a");
        InstanceWalletKeyStoreBCED25519 bob = new InstanceWalletKeyStoreBCED25519("b");
        InstanceWalletKeyStoreBCED25519 charlie = new InstanceWalletKeyStoreBCED25519("c");
        InstanceWalletKeyStoreBCED25519 delta = new InstanceWalletKeyStoreBCED25519("d");
        InstanceWalletKeyStoreBCED25519 echo = new InstanceWalletKeyStoreBCED25519("e");
         */
        InstanceWalletKeyStoreBCED25519 b = new InstanceWalletKeyStoreBCED25519("b");
        InstanceWalletKeyStoreBCED25519 e = new InstanceWalletKeyStoreBCED25519("e");
        /*
        System.out.println("a - " + alice.getPublicKeyAtIndexURL64(0));
        System.out.println("b - " + bob.getPublicKeyAtIndexURL64(0));
        System.out.println("c - " + charlie.getPublicKeyAtIndexURL64(0));
        System.out.println("d - " + delta.getPublicKeyAtIndexURL64(0));
        System.out.println("e - " + echo.getPublicKeyAtIndexURL64(0));
        System.out.println("f - " + fox.getPublicKeyAtIndexURL64(0));
        

        //Create declaration
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.DECLARATION, alice, alice, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "declaration a"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.DECLARATION, bob, bob, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "declaration b"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.DECLARATION, charlie, charlie, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "declaration c"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.DECLARATION, delta, delta, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "declaration d"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.DECLARATION, echo, echo, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "declaration e"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.DECLARATION, fox, fox, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "declaration f"));

        //register main
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.REGISTER_MAIN, alice, alice, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "REGISTER_MAIN a"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.REGISTER_MAIN, bob, bob, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "REGISTER_MAIN b"));
        //register overflow
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.REGISTER_OVERFLOW, charlie, charlie, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "REGISTER_OVERFLOW c"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.REGISTER_OVERFLOW, delta, delta, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "REGISTER_OVERFLOW d"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.REGISTER_OVERFLOW, echo, echo, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "REGISTER_OVERFLOW e"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.REGISTER_OVERFLOW, fox, fox, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "REGISTER_OVERFLOW f"));
        //assign overflow
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.ASSIGN_OVERFLOW, alice, charlie, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "ASSIGN_OVERFLOW a c"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.ASSIGN_OVERFLOW, bob, delta, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "ASSIGN_OVERFLOW b d"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.ASSIGN_OVERFLOW, bob, echo, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "ASSIGN_OVERFLOW b e"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.ASSIGN_OVERFLOW, bob, fox, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "ASSIGN_OVERFLOW b f"));
         */
        //deregister overflow
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.DEREGISTER_MAIN, b, b, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "DEREGISTER_MAIN b"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.DEREGISTER_OVERFLOW, e, e, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "DEREGISTER_OVERFLOW e"));

        transactions = new String[transactionList.size()];
        return transactionList.toArray(transactions);
    }

    public static String[] generateUnassignOverflowTransactionArray() throws TransactionCanNotBeCreatedException, TransactionNotYetImplementedException, InvalidWalletIndexException, PublicKeySerializzationException, WalletException {
        String[] transactions;
        ArrayList<String> transactionList = new ArrayList<>();
        /*
        InstanceWalletKeyStoreBCED25519 a = new InstanceWalletKeyStoreBCED25519("a");
        InstanceWalletKeyStoreBCED25519 bob = new InstanceWalletKeyStoreBCED25519("b");
        InstanceWalletKeyStoreBCED25519 c = new InstanceWalletKeyStoreBCED25519("c");
        InstanceWalletKeyStoreBCED25519 delta = new InstanceWalletKeyStoreBCED25519("d");
        InstanceWalletKeyStoreBCED25519 echo = new InstanceWalletKeyStoreBCED25519("e");
         */
        InstanceWalletKeyStoreBCED25519 a = new InstanceWalletKeyStoreBCED25519("a");
        InstanceWalletKeyStoreBCED25519 c = new InstanceWalletKeyStoreBCED25519("c");
        InstanceWalletKeyStoreBCED25519 b = new InstanceWalletKeyStoreBCED25519("b");
        InstanceWalletKeyStoreBCED25519 e = new InstanceWalletKeyStoreBCED25519("e");
        /*
        System.out.println("a - " + alice.getPublicKeyAtIndexURL64(0));
        System.out.println("b - " + bob.getPublicKeyAtIndexURL64(0));
        System.out.println("c - " + charlie.getPublicKeyAtIndexURL64(0));
        System.out.println("d - " + delta.getPublicKeyAtIndexURL64(0));
        System.out.println("e - " + echo.getPublicKeyAtIndexURL64(0));
        System.out.println("f - " + fox.getPublicKeyAtIndexURL64(0));
        

        //Create declaration
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.DECLARATION, alice, alice, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "declaration a"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.DECLARATION, bob, bob, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "declaration b"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.DECLARATION, charlie, charlie, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "declaration c"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.DECLARATION, delta, delta, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "declaration d"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.DECLARATION, echo, echo, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "declaration e"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.DECLARATION, fox, fox, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "declaration f"));

        //register main
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.REGISTER_MAIN, alice, alice, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "REGISTER_MAIN a"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.REGISTER_MAIN, bob, bob, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "REGISTER_MAIN b"));
        //register overflow
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.REGISTER_OVERFLOW, charlie, charlie, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "REGISTER_OVERFLOW c"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.REGISTER_OVERFLOW, delta, delta, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "REGISTER_OVERFLOW d"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.REGISTER_OVERFLOW, echo, echo, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "REGISTER_OVERFLOW e"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.REGISTER_OVERFLOW, fox, fox, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "REGISTER_OVERFLOW f"));
        //assign overflow
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.ASSIGN_OVERFLOW, alice, charlie, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "ASSIGN_OVERFLOW a c"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.ASSIGN_OVERFLOW, bob, delta, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "ASSIGN_OVERFLOW b d"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.ASSIGN_OVERFLOW, bob, echo, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "ASSIGN_OVERFLOW b e"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.ASSIGN_OVERFLOW, bob, fox, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "ASSIGN_OVERFLOW b f"));
         */
        //deregister overflow
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.UNASSIGN_OVERFLOW, c, a, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "unassign overflow c a"));
        transactionList.add(getTransactionJson(KeyContexts.TransactionType.UNASSIGN_OVERFLOW, a, c, 0, 0, new BigInteger("10000000000000"), new BigInteger("10000000000000"), "unassign overflow b e"));

        transactions = new String[transactionList.size()];
        return transactionList.toArray(transactions);
    }

    public static String getTransactionJson(KeyContexts.TransactionType type, InstanceWalletKeyStoreBCED25519 from, InstanceWalletKeyStoreBCED25519 to, int fromIndex, int toIndex, BigInteger green, BigInteger red, String message) throws TransactionNotYetImplementedException, InvalidWalletIndexException, PublicKeySerializzationException, TransactionCanNotBeCreatedException, WalletException {
        String result;

        InternalTransactionBean itb
                = getInternalTransactionBean(
                        type,
                        from.getPublicKeyAtIndexURL64(fromIndex),
                        to.getPublicKeyAtIndexURL64(toIndex),
                        green,
                        red,
                        message
                );

        TransactionBean genericTRA = TkmWallet.createGenericTransaction(
                itb,
                from,
                0);
        result = TkmTextUtils.toJson(genericTRA);

        return result;
    }

    public static String getTransactionJson(KeyContexts.TransactionType type, InstanceWalletKeystoreInterface from, InstanceWalletKeystoreInterface to, int fromIndex, int toIndex, BigInteger green, BigInteger red, String message) throws TransactionNotYetImplementedException, InvalidWalletIndexException, PublicKeySerializzationException, TransactionCanNotBeCreatedException, WalletException {
        String result;

        InternalTransactionBean itb
                = getInternalTransactionBean(
                        type,
                        from.getPublicKeyAtIndexURL64(fromIndex),
                        to.getPublicKeyAtIndexURL64(toIndex),
                        green,
                        red,
                        message
                );

        TransactionBean genericTRA = TkmWallet.createGenericTransaction(
                itb,
                from,
                fromIndex);
        result = TkmTextUtils.toJson(genericTRA);

        return result;
    }

    public static String getTransactionJson(KeyContexts.TransactionType type, InstanceWalletKeystoreInterface from, InstanceWalletKeystoreInterface to, int fromIndex, int toIndex, BigInteger green, BigInteger red, String message, Date notBefore, int epoch, int slot)
            throws TransactionNotYetImplementedException, InvalidWalletIndexException, PublicKeySerializzationException, TransactionCanNotBeCreatedException, WalletException {
        String result;

        InternalTransactionBean itb
                = getTransactionBean(
                        type,
                        from.getPublicKeyAtIndexURL64(fromIndex),
                        to.getPublicKeyAtIndexURL64(toIndex),
                        green,
                        red,
                        message,
                        notBefore,
                        epoch,
                        slot
                );

        TransactionBean genericTRA = TkmWallet.createGenericTransaction(
                itb,
                from,
                fromIndex);
        result = TkmTextUtils.toJson(genericTRA);

        return result;
    }

    public static String getTransactionJson(KeyContexts.TransactionType type, InstanceWalletKeystoreInterface from, String to, int fromIndex, int toIndex, BigInteger green, BigInteger red, String message, Date notBefore, int epoch, int slot)
            throws TransactionNotYetImplementedException, InvalidWalletIndexException, PublicKeySerializzationException, TransactionCanNotBeCreatedException, WalletException {
        String result;

        InternalTransactionBean itb
                = getTransactionBean(
                        type,
                        from.getPublicKeyAtIndexURL64(fromIndex),
                        to,
                        green,
                        red,
                        message,
                        notBefore,
                        epoch,
                        slot
                );

        TransactionBean genericTRA = TkmWallet.createGenericTransaction(
                itb,
                from,
                fromIndex);
        result = TkmTextUtils.toJson(genericTRA);

        return result;
    }
    public static String[] generateTransactionArrayWithPaySU(int numberOfTransaction, KeyContexts.TransactionType type, String walletPrefix, int numberOfFromWallets, int numberOfToWallets, int fromWalletSpace, int toWalletSpace) throws TransactionCanNotBeCreatedException, TransactionNotYetImplementedException, InvalidWalletIndexException, PublicKeySerializzationException, UnlockWalletException {
        String[] transactions = new String[numberOfTransaction * 3];
        System.out.println("number of transactions: " + transactions.length);
        InstanceWalletKeyStoreBCED25519[] fromKeys = new InstanceWalletKeyStoreBCED25519[numberOfFromWallets];
        InstanceWalletKeyStoreBCED25519[] toKeys = new InstanceWalletKeyStoreBCED25519[numberOfToWallets];
        System.out.println("From keyspace: " + numberOfFromWallets * fromWalletSpace);
        System.out.println("To keyspace: " + numberOfFromWallets * fromWalletSpace);
        for (int i = 0; i < fromKeys.length; i++) {
            fromKeys[i] = new InstanceWalletKeyStoreBCED25519(walletPrefix + "_tkm_test_factory_demo_from_wallet_" + i);
        }
        for (int i = 0; i < toKeys.length; i++) {
            toKeys[i] = new InstanceWalletKeyStoreBCED25519(walletPrefix + "_tkm_test_factory_demo_to_wallet_" + i);
        }
        System.out.print("Progress: 0%");
        int indPrg = numberOfTransaction / 20;
        boolean firstRun = false;
        for (int i = 0; i < numberOfTransaction; i++) {
            if (i % indPrg == 0) {
                if (firstRun) {
                    float val = i;
                    System.out.print("..." + (int) (val / numberOfTransaction * 100) + "%");
                } else {
                    firstRun = true;
                }
            }

            int fromWallet = pickANumberInRange(0, numberOfFromWallets);
            int toWallet = pickANumberInRange(0, numberOfToWallets);
            int fromKey = pickANumberInRange(0, fromWalletSpace);
            int toKey = pickANumberInRange(0, toWalletSpace);

            InternalTransactionBean itb = getInternalTransactionBean(
                    type,
                    fromKeys[fromWallet].getPublicKeyAtIndexURL64(fromKey),
                    toKeys[toWallet].getPublicKeyAtIndexURL64(toKey),
                    pickANumberAsCoin(10),
                    pickANumberAsCoin(10),
                    createIdentifingMessage(walletPrefix, fromWallet, fromKey, toWallet, toKey)
            );

            TransactionBean genericTR = TkmWallet.createGenericTransaction(
                    itb,
                    fromKeys[fromWallet],
                    fromKey);

            transactions[i] = TkmTextUtils.toJson(
                    genericTR
            );

            transactions[i + numberOfTransaction] = TkmTextUtils.toJson(
                    TkmWallet.createGenericTransaction(
                            getInternalTransactionBean(
                                    KeyContexts.TransactionType.PAY,
                                    toKeys[toWallet].getPublicKeyAtIndexURL64(toKey),
                                    fromKeys[fromWallet].getPublicKeyAtIndexURL64(fromKey),
                                    itb.getGreenValue().divide(BigInteger.ONE.add(BigInteger.ONE)),
                                    itb.getRedValue(),
                                    createIdentifingMessage(walletPrefix, fromWallet, fromKey, toWallet, toKey) + " PAY"
                            ),
                            fromKeys[fromWallet],
                            fromKey)
            );

            transactions[i + (numberOfTransaction * 2)] = TkmTextUtils.toJson(
                    TkmWallet.createGenericTransaction(
                            getInternalTransactionBean(
                                    KeyContexts.TransactionType.STAKE_UNDO,
                                    fromKeys[fromWallet].getPublicKeyAtIndexURL64(fromKey),
                                    null,
                                    null,
                                    null,
                                    createIdentifingMessage(walletPrefix, fromWallet, fromKey, toWallet, toKey) + " STAKE_UNDO"
                            ),
                            fromKeys[fromWallet],
                            fromKey)
            );

        }
        System.out.println("...100%");
        return transactions;
    }
    public static InternalTransactionBean getInternalTransactionBean(KeyContexts.TransactionType type, String from, String to, BigInteger greenValue, BigInteger redValue, String message) throws TransactionNotYetImplementedException {
        InternalTransactionBean itb = null;
        switch (type) {
            case PAY:
                itb = BuilderITB.pay(from, to, greenValue, redValue, message);
                break;
            case DECLARATION:
                itb = BuilderITB.declaration(to, greenValue, redValue, message);
                break;
            case REGISTER_MAIN:
                itb = registerMain(from, to, message);
                break;
            case REGISTER_OVERFLOW:
                itb = registerOverflow(from, to, message);
                break;
            case DEREGISTER_OVERFLOW:
                itb = BuilderITB.deregisterOverflow(from, message);
                break;
            case DEREGISTER_MAIN:
                itb = BuilderITB.deregisterMain(from, message);
                break;
            case ASSIGN_OVERFLOW:
                itb = BuilderITB.assignOverflow(from, to, message);
                break;
            case UNASSIGN_OVERFLOW:
                itb = BuilderITB.unassignOverflow(from, to, message);
                break;
            case STAKE_UNDO:
                itb = BuilderITB.stakeUndo(from, message);
                break;
            case STAKE:
                itb = BuilderITB.stake(from, to, greenValue, message);
                break;
            case BLOB:
                itb = blob(from, to, message);
                break;
            default:
                throw new TransactionNotYetImplementedException("NOT YET IMPLEMENTED: " + type.name());
        }
        return itb;
    }
    
    /**
     * 
     * @param type
     * @param from
     * @param to
     * @param greenValue
     * @param redValue
     * @param message
     * @param epoch
     * @param slot
     * @param notBefore
     * @return
     * @throws TransactionNotYetImplementedException 
     */
    public static InternalTransactionBean getTestInternalTransactionBean(KeyContexts.TransactionType type, String from, String to, BigInteger greenValue, BigInteger redValue, String message, Integer epoch, Integer slot, Date notBefore) throws TransactionNotYetImplementedException {
        InternalTransactionBean itb = null;
        switch (type) {
            case PAY:
            case DECLARATION:
            case REGISTER_MAIN:
            case REGISTER_OVERFLOW:
            case DEREGISTER_OVERFLOW:
            case DEREGISTER_MAIN:
            case ASSIGN_OVERFLOW:
            case UNASSIGN_OVERFLOW:
            case STAKE_UNDO:
            case STAKE:
            case BLOB:
                itb = BuilderITB.test(type, from, to, message, greenValue, redValue, epoch, slot, notBefore);
                break;
            default:
                throw new TransactionNotYetImplementedException("NOT YET IMPLEMENTED: " + type.name());
        }
        return itb;
    }

    public static InternalTransactionBean getTransactionBean(KeyContexts.TransactionType type, String from, String to, BigInteger greenValue, BigInteger redValue, String message, Date notBefore, int epoch, int slot) throws TransactionNotYetImplementedException {
        InternalTransactionBean itb = null;
        switch (type) {
            case COINBASE:
                itb = BuilderITB.coinBase(to, epoch, slot, greenValue, redValue, message, notBefore);
                break;
            case PAY:
                itb = BuilderITB.pay(from, to, greenValue, redValue, message, notBefore);
                break;
            case DECLARATION:
                itb = BuilderITB.declaration(to, greenValue, redValue, message, notBefore);
                break;
            case REGISTER_MAIN:
                itb = registerMain(from, to, message, notBefore);
                break;
            case REGISTER_OVERFLOW:
                itb = registerOverflow(from, to, message, notBefore);
                break;
            case DEREGISTER_OVERFLOW:
                itb = BuilderITB.deregisterOverflow(from, message, notBefore);
                break;
            case DEREGISTER_MAIN:
                itb = BuilderITB.deregisterMain(from, message, notBefore);
                break;
            case ASSIGN_OVERFLOW:
                itb = BuilderITB.assignOverflow(from, to, message, notBefore);
                break;
            case UNASSIGN_OVERFLOW:
                itb = BuilderITB.unassignOverflow(from, to, message, notBefore);
                break;
            case STAKE_UNDO:
                itb = BuilderITB.stakeUndo(from, message, notBefore);
                break;
            case STAKE:
                itb = BuilderITB.stake(from, to, greenValue, message, notBefore);
                break;
            case BLOB:
                itb = blob(from, to, message, notBefore);
                break;
            case S_CONTRACT_DEPLOY:
                itb = BuilderITB.contractDeploy(from, message, notBefore);
                break;
            case S_CONTRACT_CALL:
                itb = BuilderITB.contractCall(from, message, message, notBefore);
                break;

            default:
                throw new TransactionNotYetImplementedException();
        }
        return itb;
    }
    
    public static String createIdentifingMessage(String header, int fromWalletNumber, int fromWalletKey, int toWalletNumber, int toWalletKey) {
        StringBuilder sb = new StringBuilder();
        sb.append(header).append(" ")
                .append("from_wallet (").append(fromWalletNumber).append(",").append(fromWalletKey).append(") ")
                .append("to to_wallet (").append(toWalletNumber).append(",").append(toWalletKey).append(") ");
        return sb.toString();
    }
    
    public static InstanceWalletKeystoreInterface[] generateWalletList(String basename, int numAddr, KeyContexts.WalletCypher cy) throws TransactionNotYetImplementedException, UnlockWalletException, WalletBurnedException, WalletEmptySeedException {
        InstanceWalletKeystoreInterface[] iwk = new InstanceWalletKeystoreInterface[numAddr];
        for (int i = 0; i < numAddr; i++) {
            switch (cy) {
                case BCQTESLA_PS_1:
                    iwk[i] = new InstanceWalletKeyStoreBCQTESLAPSSC1Round1(basename + "_test_" + cy.name() + "_" + i, 800);
                    break;
                case BCQTESLA_PS_1_R2:
                    iwk[i] = new InstanceWalletKeyStoreBCQTESLAPSSC1Round2(basename + "_test_" + cy.name() + "_" + i, 800);
                    break;
                case Ed25519BC:
                    iwk[i] = new InstanceWalletKeyStoreBCED25519(basename + "_test_" + cy.name() + "_" + i, 800);
                    break;
                default:
                    throw new TransactionNotYetImplementedException(cy.name());
            }

        }
        Arrays.sort(iwk);
        return iwk;
    }
    
    public static String[] generateStakeholderDeclarations(InstanceWalletKeystoreInterface[] keys) {
        String[] holdersDeclarations = new String[keys.length];
        //System.out.println(Arrays.binarySearch(keys, keys[4]));
        Arrays.stream(keys).parallel()
                .forEach((InstanceWalletKeystoreInterface ikw) -> {
                    int num = pickANumberInRange(0, keys.length);
                    try {
                        holdersDeclarations[Arrays.binarySearch(keys, ikw)]
                                = TkmTextUtils.toJson(TkmWallet.createGenericTransaction(getInternalTransactionBean(
                                        KeyContexts.TransactionType.DECLARATION,
                                        ikw.getPublicKeyAtIndexURL64(0),
                                        ikw.getPublicKeyAtIndexURL64(0),
                                        TkmTK.unitTK(4800),
                                        TkmTK.unitTK(1200),
                                        createIdentifingMessage("stake holder", Arrays.binarySearch(keys, ikw), 0, Arrays.binarySearch(keys, ikw), 0)
                                ),
                                        ikw,
                                        0)
                                );
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
        Arrays.sort(holdersDeclarations);
        return holdersDeclarations;
    }
    
    public static String[] generateStakeholderDeclarations(InstanceWalletKeystoreInterface[] keys, int greenTK, int redTK) {
        String[] holdersDeclarations = new String[keys.length];
        //System.out.println(Arrays.binarySearch(keys, keys[4]));
        Arrays.stream(keys).parallel()
                .forEach((InstanceWalletKeystoreInterface ikw) -> {
                    int num = pickANumberInRange(0, keys.length);
                    try {
                        holdersDeclarations[Arrays.binarySearch(keys, ikw)]
                                = TkmTextUtils.toJson(TkmWallet.createGenericTransaction(getInternalTransactionBean(
                                        KeyContexts.TransactionType.DECLARATION,
                                        ikw.getPublicKeyAtIndexURL64(0),
                                        ikw.getPublicKeyAtIndexURL64(0),
                                        TkmTK.unitTK(greenTK),
                                        TkmTK.unitTK(redTK),
                                        createIdentifingMessage("stake holder", Arrays.binarySearch(keys, ikw), 0, Arrays.binarySearch(keys, ikw), 0)
                                ),
                                        ikw,
                                        0)
                                );
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
        Arrays.sort(holdersDeclarations);
        return holdersDeclarations;
    }
    
    public static String[] generateRedWalletsDeclarations(InstanceWalletKeystoreInterface[] keys) {
        return generateStakeholderDeclarations(keys, 4800, 1200);
    }
    
    public static String[] generateRegisterMain(InstanceWalletKeystoreInterface[] keys) {
        String[] holdersDeclarations = new String[keys.length];
        //System.out.println(Arrays.binarySearch(keys, keys[4]));
        Arrays.stream(keys).parallel()
                .forEach((InstanceWalletKeystoreInterface ikw) -> {
                    int num = pickANumberInRange(0, keys.length);
                    try {
                        holdersDeclarations[Arrays.binarySearch(keys, ikw)]
                                = TkmTextUtils.toJson(TkmWallet.createGenericTransaction(getInternalTransactionBean(
                                        KeyContexts.TransactionType.REGISTER_MAIN,
                                        ikw.getPublicKeyAtIndexURL64(0),
                                        null,
                                        null,
                                        null,
                                        createIdentifingMessage("register main", Arrays.binarySearch(keys, ikw), 0, Arrays.binarySearch(keys, ikw), 0)
                                ),
                                        ikw,
                                        0)
                                );
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
        Arrays.sort(holdersDeclarations);
        return holdersDeclarations;
    }
    
    public static String[] generateRegisterOverflow(InstanceWalletKeystoreInterface[] keys) {
        String[] holdersDeclarations = new String[keys.length];
        //System.out.println(Arrays.binarySearch(keys, keys[4]));
        Arrays.stream(keys).parallel()
                .forEach((InstanceWalletKeystoreInterface ikw) -> {
                    int num = pickANumberInRange(0, keys.length);
                    try {
                        holdersDeclarations[Arrays.binarySearch(keys, ikw)]
                                = TkmTextUtils.toJson(TkmWallet.createGenericTransaction(getInternalTransactionBean(
                                        KeyContexts.TransactionType.REGISTER_OVERFLOW,
                                        ikw.getPublicKeyAtIndexURL64(0),
                                        null,
                                        null,
                                        null,
                                        createIdentifingMessage("register overflow", Arrays.binarySearch(keys, ikw), 0, Arrays.binarySearch(keys, ikw), 0)
                                ),
                                        ikw,
                                        0)
                                );
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
        Arrays.sort(holdersDeclarations);
        return holdersDeclarations;
    }
    
    public static String[] generateAssignOverflowToMain(InstanceWalletKeystoreInterface[] mainKeys, InstanceWalletKeystoreInterface[] overflowKeys) {
        String[] assign = new String[overflowKeys.length];
        int totlaMains = mainKeys.length;
        Arrays.stream(mainKeys).parallel()
                .forEach((InstanceWalletKeystoreInterface mainIwk) -> {
                    int mainNum = Arrays.binarySearch(mainKeys, mainIwk);
                    Arrays.stream(overflowKeys).parallel()
                            .filter((InstanceWalletKeystoreInterface owK) -> {
                                return Arrays.binarySearch(overflowKeys, owK) % totlaMains == mainNum;
                            })
                            .forEach((InstanceWalletKeystoreInterface owK) -> {
                                try {
                                    assign[Arrays.binarySearch(overflowKeys, owK)] = TkmTextUtils.toJson(TkmWallet.createGenericTransaction(getInternalTransactionBean(
                                            KeyContexts.TransactionType.ASSIGN_OVERFLOW,
                                            mainIwk.getPublicKeyAtIndexURL64(0),
                                            owK.getPublicKeyAtIndexURL64(0),
                                            null,
                                            null,
                                            createIdentifingMessage("assign overflow", Arrays.binarySearch(mainKeys, mainIwk), 0, Arrays.binarySearch(overflowKeys, owK), 0)
                                    ),
                                            mainIwk,
                                            0)
                                    );
                                } catch (WalletException ex) {
                                    Logger.getLogger(TransactionGenerator.class.getName()).log(Level.SEVERE, null, ex);
                                } catch (TransactionNotYetImplementedException ex) {
                                    Logger.getLogger(TransactionGenerator.class.getName()).log(Level.SEVERE, null, ex);
                                }
                            });
                    //assign[]
                });
        Arrays.sort(assign);
        return assign;
    }
    
    public static String[] generatStakeToMain(InstanceWalletKeystoreInterface[] mainKeys, InstanceWalletKeystoreInterface[] holderKeys) {
        String[] assign = new String[holderKeys.length];
        int totlaMains = mainKeys.length;
        Arrays.stream(mainKeys).parallel()
                .forEach((InstanceWalletKeystoreInterface mainIwk) -> {
                    int mainNum = Arrays.binarySearch(mainKeys, mainIwk);
                    Arrays.stream(holderKeys).parallel()
                            .filter((InstanceWalletKeystoreInterface owK) -> {
                                return Arrays.binarySearch(holderKeys, owK) % totlaMains == mainNum;
                            })
                            .forEach((InstanceWalletKeystoreInterface owK) -> {
                                try {
                                    assign[Arrays.binarySearch(holderKeys, owK)] = TkmTextUtils.toJson(TkmWallet.createGenericTransaction(getInternalTransactionBean(
                                            KeyContexts.TransactionType.STAKE,
                                            owK.getPublicKeyAtIndexURL64(0),
                                            mainIwk.getPublicKeyAtIndexURL64(0),
                                            TkmTK.unitTK(1990000000),
                                            null,
                                            createIdentifingMessage("stake to main", Arrays.binarySearch(holderKeys, owK), 0, Arrays.binarySearch(mainKeys, mainIwk), 0)
                                    ),
                                            owK,
                                            0)
                                    );
                                } catch (WalletException ex) {
                                    Logger.getLogger(TransactionGenerator.class.getName()).log(Level.SEVERE, null, ex);
                                } catch (TransactionNotYetImplementedException ex) {
                                    Logger.getLogger(TransactionGenerator.class.getName()).log(Level.SEVERE, null, ex);
                                }
                            });
                    //assign[]
                });
        Arrays.sort(assign);
        return assign;
    }
    
    public static String[] generatePayToRed(InstanceWalletKeystoreInterface[] from, InstanceWalletKeystoreInterface[] to, BigInteger value) {
        String[] assign = new String[to.length];
        int totlaMains = from.length;
        Arrays.stream(to).parallel().forEach((InstanceWalletKeystoreInterface iwkTo) -> {
            try {
                assign[Arrays.binarySearch(to, iwkTo)] = TkmTextUtils.toJson(TkmWallet.createGenericTransaction(getInternalTransactionBean(
                        KeyContexts.TransactionType.PAY,
                        from[Arrays.binarySearch(to, iwkTo) % totlaMains].getPublicKeyAtIndexURL64(0),
                        iwkTo.getPublicKeyAtIndexURL64(0),
                        null,
                        value,
                        createIdentifingMessage("pay to ", Arrays.binarySearch(from, from[Arrays.binarySearch(to, iwkTo) % totlaMains]), 0, Arrays.binarySearch(to, iwkTo), 0)
                ),
                        from[Arrays.binarySearch(to, iwkTo) % totlaMains],
                        0)
                );
            } catch (WalletException | TransactionNotYetImplementedException ex) {
                Logger.getLogger(TransactionGenerator.class.getName()).log(Level.SEVERE, null, ex);
            }

        });
        Arrays.sort(assign);
        return assign;
    }
    
    public static String[] generatePayToGreen(InstanceWalletKeystoreInterface[] from, InstanceWalletKeystoreInterface[] to, BigInteger value) {
        String[] assign = new String[to.length];
        int totlaMains = from.length;
        Arrays.stream(to).parallel().forEach((InstanceWalletKeystoreInterface iwkTo) -> {
            try {
                assign[Arrays.binarySearch(to, iwkTo)] = TkmTextUtils.toJson(TkmWallet.createGenericTransaction(getInternalTransactionBean(
                        KeyContexts.TransactionType.PAY,
                        from[Arrays.binarySearch(to, iwkTo) % totlaMains].getPublicKeyAtIndexURL64(0),
                        iwkTo.getPublicKeyAtIndexURL64(0),
                        value,
                        null,
                        createIdentifingMessage("pay to ", Arrays.binarySearch(from, from[Arrays.binarySearch(to, iwkTo) % totlaMains]), 0, Arrays.binarySearch(to, iwkTo), 0)
                ),
                        from[Arrays.binarySearch(to, iwkTo) % totlaMains],
                        0)
                );
            } catch (WalletException | TransactionNotYetImplementedException ex) {
                Logger.getLogger(TransactionGenerator.class.getName()).log(Level.SEVERE, null, ex);
            }

        });
        Arrays.sort(assign);
        return assign;
    }
    
    public static String[] mergeStringArrays(String[]... args) {
        ConcurrentSkipListSet<String> merged = new ConcurrentSkipListSet<String>();
        for (String[] arg : args) {
            Arrays.stream(arg).parallel()
                    .forEach((String trx) -> {
                        merged.add(trx);
                    });
        }
        String[] mergedArray = merged.toArray(new String[merged.size()]);
        Arrays.sort(mergedArray);
        return mergedArray;
    }

    public static TransactionBox[] mergeTboxArrays(TransactionBox[]... args) {
        ConcurrentSkipListSet<TransactionBox> merged = new ConcurrentSkipListSet<TransactionBox>();
        for (TransactionBox[] arg : args) {
            Arrays.stream(arg).parallel()
                    .filter((TransactionBox tbox) -> {
                        return tbox != null && tbox.isValid();
                    })
                    .forEach((TransactionBox trx) -> {
                        merged.add(trx);
                    });
        }
        TransactionBox[] mergedArray = merged.toArray(new TransactionBox[merged.size()]);
        Arrays.sort(mergedArray);
        return mergedArray;
    }

    /**
     * [min,max)
     *
     * @param min
     * @param max
     * @return
     */
    public static int pickANumberInRange(int min, int max) {
        int lenMax = ("" + max).length();
        int val = 0;
        do {
            val = Integer.parseInt(RandomStringUtils.randomNumeric(lenMax, lenMax));
        } while (val < min | val >= max);
        return val;
    }

    public static BigInteger pickANumberAsCoin(int digits) {
        BigInteger res = new BigInteger(RandomStringUtils.randomNumeric(digits, digits)).abs();
        //res = res.multiply(KeyContexts.NUMBER_OF_ZEROS_SHIFT_DECIMAL);
        return TkmTK.unitTK(res);
    }

    public static BigInteger pickABigNumberInRange(BigInteger min, BigInteger max) {
        BigInteger diff = max.subtract(min);
        int len = diff.bitLength();
        Random rand = new Random();
        BigInteger res = new BigInteger(len, rand);
        if (res.compareTo(min) < 0) {
            res = res.add(min);
        }
        if (res.compareTo(diff) >= 0) {
            res = res.mod(diff).add(min);
        }
        return res;
    }

    private static InternalTransactionBean registerMain(String from, String to, String message, Date notBefore) {
        try {
            InternalTransactionBean registerMain = BuilderITB.registerMain(from, message, notBefore);
            registerMain.setTo(to);
            registerMain.setTransactionHash(TkmTextUtils.internalTransactionBeanHash(registerMain));
            return registerMain;
        } catch (NullInternalTransactionBeanException | HashCompositionException ex) {
            log.error("Error creating register main transaction", ex);
            return null;
        }
    }

    private static InternalTransactionBean registerOverflow(String from, String to, String message, Date notBefore) {
        try {
            InternalTransactionBean registerOverflow = BuilderITB.registerOverflow(from, message, notBefore);
            registerOverflow.setTo(to);
            registerOverflow.setTransactionHash(TkmTextUtils.internalTransactionBeanHash(registerOverflow));
            return registerOverflow;
        } catch (NullInternalTransactionBeanException | HashCompositionException ex) {
            log.error("Error creating register main transaction", ex);
            return null;
        }
    }

    private static InternalTransactionBean blob(String from, String to, String message, Date notBefore) {
        try {
            InternalTransactionBean blob = BuilderITB.blob(from, message, notBefore);
            blob.setTo(to);
            blob.setTransactionHash(TkmTextUtils.internalTransactionBeanHash(blob));
            return blob;
        } catch (NullInternalTransactionBeanException | HashCompositionException ex) {
            log.error("Error creating register main transaction", ex);
            return null;
        }
    }

    private static InternalTransactionBean registerMain(String from, String to, String message) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    private static InternalTransactionBean registerOverflow(String from, String to, String message) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }


    private static InternalTransactionBean blob(String from, String to, String message) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }
}
