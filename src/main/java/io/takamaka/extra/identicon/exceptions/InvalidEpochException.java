/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.takamaka.extra.identicon.exceptions;

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public class InvalidEpochException extends TimeException {

    private static final long serialVersionUID = 1L;

    public InvalidEpochException() {
        super();
    }

    public InvalidEpochException(String msg) {
        super(msg);
    }

    public InvalidEpochException(Throwable er) {
        super(er);
    }

    public InvalidEpochException(String msg, Throwable er) {
        super(msg, er);
    }
}
