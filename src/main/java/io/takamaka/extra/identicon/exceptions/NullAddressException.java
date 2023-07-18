/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.takamaka.extra.identicon.exceptions;

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public class NullAddressException extends AddressException {

    private static final long serialVersionUID = 1L;

    public NullAddressException() {
        super();
    }

    public NullAddressException(String msg) {
        super(msg);
    }

    public NullAddressException(Throwable er) {
        super(er);
    }

    public NullAddressException(String msg, Throwable er) {
        super(msg, er);
    }
}
