/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.takamaka.extra.identicon.exceptions;

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public class InvalidSlotException extends TimeException {

    private static final long serialVersionUID = 1L;

    public InvalidSlotException() {
        super();
    }

    public InvalidSlotException(String msg) {
        super(msg);
    }

    public InvalidSlotException(Throwable er) {
        super(er);
    }

    public InvalidSlotException(String msg, Throwable er) {
        super(msg, er);
    }
}
