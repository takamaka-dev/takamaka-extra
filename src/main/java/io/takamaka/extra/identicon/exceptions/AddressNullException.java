/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.takamaka.extra.identicon.exceptions;

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public class AddressNullException extends AddressException {

    private static final long serialVersionUID = 1L;

    public AddressNullException() {
        super();
    }

    public AddressNullException(String msg) {
        super(msg);
    }

    public AddressNullException(Throwable er) {
        super(er);
    }

    public AddressNullException(String msg, Throwable er) {
        super(msg, er);
    }
}
