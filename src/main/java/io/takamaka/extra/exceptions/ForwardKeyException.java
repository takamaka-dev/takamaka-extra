/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.takamaka.extra.exceptions;

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public class ForwardKeyException extends Exception {

    private static final long serialVersionUID = 1L;

    public ForwardKeyException() {
        super();
    }

    public ForwardKeyException(String msg) {
        super(msg);
    }

    public ForwardKeyException(Throwable er) {
        super(er);
    }

    public ForwardKeyException(String msg, Throwable er) {
        super(msg, er);
    }
}
