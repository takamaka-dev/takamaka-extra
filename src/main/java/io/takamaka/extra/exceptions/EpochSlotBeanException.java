/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.takamaka.extra.exceptions;

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public class EpochSlotBeanException extends Exception {

    private static final long serialVersionUID = 1L;

    public EpochSlotBeanException() {
        super();
    }

    public EpochSlotBeanException(String msg) {
        super(msg);
    }

    public EpochSlotBeanException(Throwable er) {
        super(er);
    }

    public EpochSlotBeanException(String msg, Throwable er) {
        super(msg, er);
    }
}
