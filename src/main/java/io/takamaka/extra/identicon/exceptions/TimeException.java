/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.takamaka.extra.identicon.exceptions;

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public class TimeException extends Exception {

    private static final long serialVersionUID = 1L;

    public TimeException() {
        super();
    }

    public TimeException(String msg) {
        super(msg);
    }

    public TimeException(Throwable er) {
        super(er);
    }

    public TimeException(String msg, Throwable er) {
        super(msg, er);
    }
}
