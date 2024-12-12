/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.takamaka.extra.exceptions;

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public class TkmCryptoExtraException extends Exception {

    private static final long serialVersionUID = 2412970486957518376L;

    public TkmCryptoExtraException() {
        super();
    }

    public TkmCryptoExtraException(String msg) {
        super(msg);
    }

    public TkmCryptoExtraException(Throwable er) {
        super(er);
    }

    public TkmCryptoExtraException(String msg, Throwable er) {
        super(msg, er);
    }
}
