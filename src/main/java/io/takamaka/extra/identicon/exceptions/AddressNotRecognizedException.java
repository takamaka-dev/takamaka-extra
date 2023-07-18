/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.takamaka.extra.identicon.exceptions;

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public class AddressNotRecognizedException extends AddressException {

    private static final long serialVersionUID = 1L;

    public AddressNotRecognizedException() {
        super();
    }

    public AddressNotRecognizedException(String msg) {
        super(msg);
    }

    public AddressNotRecognizedException(Throwable er) {
        super(er);
    }

    public AddressNotRecognizedException(String msg, Throwable er) {
        super(msg, er);
    }
}
