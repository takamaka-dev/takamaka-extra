/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.takamaka.extra.identicon.exceptions;

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public class DecodeForwardKeysException extends DecodeException {

    private static final long serialVersionUID = 1L;

    public DecodeForwardKeysException() {
        super();
    }

    public DecodeForwardKeysException(String msg) {
        super(msg);
    }

    public DecodeForwardKeysException(Throwable er) {
        super(er);
    }

    public DecodeForwardKeysException(String msg, Throwable er) {
        super(msg, er);
    }
}
