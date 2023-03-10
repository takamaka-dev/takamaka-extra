/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.takamaka.extra.identicon.exceptions;

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public class IdenticonException extends Exception {

    private static final long serialVersionUID = 1L;

    public IdenticonException() {
        super();
    }

    public IdenticonException(String msg) {
        super(msg);
    }

    public IdenticonException(Throwable er) {
        super(er);
    }

    public IdenticonException(String msg, Throwable er) {
        super(msg, er);
    }
}
