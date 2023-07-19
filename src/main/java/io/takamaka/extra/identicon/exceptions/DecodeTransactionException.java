/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.takamaka.extra.identicon.exceptions;

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public class DecodeTransactionException extends DecodeException {

    private static final long serialVersionUID = 1L;

    public DecodeTransactionException() {
        super();
    }

    public DecodeTransactionException(String msg) {
        super(msg);
    }

    public DecodeTransactionException(Throwable er) {
        super(er);
    }

    public DecodeTransactionException(String msg, Throwable er) {
        super(msg, er);
    }
}
