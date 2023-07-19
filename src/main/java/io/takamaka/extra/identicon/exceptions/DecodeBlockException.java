/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.takamaka.extra.identicon.exceptions;

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public class DecodeBlockException extends DecodeException {

    private static final long serialVersionUID = 1L;

    public DecodeBlockException() {
        super();
    }

    public DecodeBlockException(String msg) {
        super(msg);
    }

    public DecodeBlockException(Throwable er) {
        super(er);
    }

    public DecodeBlockException(String msg, Throwable er) {
        super(msg, er);
    }
}
