/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.core.io.transport.serial;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.TooManyListenersException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Interface for a serial port.
 *
 * <p>
 * This interface is similar to the serial port of the 'Java Communications API'.
 *
 * @author Markus Rathgeb - Initial contribution
 * @author Kai Kreuzer - added further methods
 * @author Vita Tucek - added further methods
 */
@NonNullByDefault
public interface SerialPort extends Closeable {

    int DATABITS_5 = 5;
    int DATABITS_6 = 6;
    int DATABITS_7 = 7;
    int DATABITS_8 = 8;
    int PARITY_NONE = 0;
    int PARITY_ODD = 1;
    int PARITY_EVEN = 2;
    int PARITY_MARK = 3;
    int PARITY_SPACE = 4;
    int STOPBITS_1 = 1;
    int STOPBITS_2 = 2;
    int STOPBITS_1_5 = 3;
    int FLOWCONTROL_NONE = 0;
    int FLOWCONTROL_RTSCTS_IN = 1;
    int FLOWCONTROL_RTSCTS_OUT = 2;
    int FLOWCONTROL_XONXOFF_IN = 4;
    int FLOWCONTROL_XONXOFF_OUT = 8;

    @Override
    void close();

    /**
     * Sets serial port parameters.
     *
     * @param baudrate the baud rate
     * @param dataBits the number of data bits
     * @param stopBits the number of stop bits
     * @param parity the parity
     * @throws UnsupportedCommOperationException if the operation is not supported
     */
    void setSerialPortParams(int baudrate, int dataBits, int stopBits, int parity)
            throws UnsupportedCommOperationException;

    /**
     * Gets port baud rate.
     *
     * @return baud rate
     */
    int getBaudRate();

    /**
     * Gets number of port data bits.
     *
     * @return data bits
     */
    int getDataBits();

    /**
     * Gets number of port stop bits.
     *
     * @return stop bits count
     */
    int getStopBits();

    /**
     * Gets port parity.
     *
     * @return parity
     */
    int getParity();

    /**
     * Returns an input stream.
     *
     * <p>
     * This is the only way to receive data from the communications port.
     * If the port is unidirectional and doesn't support receiving data, then getInputStream returns null.
     *
     * <p>
     * The read behaviour of the input stream returned by getInputStream depends on combination of the threshold and
     * timeout values. The possible behaviours are described in the table below:
     *
     * | Threshold .........| Timeout ........ | Read Buffer Size | Read Behaviour |
     * | State ...| Value ..| State ...| Value |
     * | disabled | - ......| disabled | - ....| n bytes .........| block until any data is available
     * | enabled .| m bytes | disabled | - ....| n bytes .........| block until min(m,n) bytes are available
     * | disabled | - ......| enabled .| x ms .| n bytes .........| block for x ms or until any data is available
     * | enabled .| m bytes | enabled .| x ms .| n bytes .........| block for x ms or until min(m,n) bytes are available
     *
     * <p>
     * Note, however, that framing errors may cause the Timeout and Threshold values to complete prematurely without
     * raising an exception.
     *
     * <p>
     * Enabling the Timeout OR Threshold with a value a zero is a special case. This causes the underlying driver to
     * poll for incoming data instead being event driven. Otherwise, the behaviour is identical to having both the
     * Timeout and Threshold disabled.
     * *
     *
     * @return the input stream or null
     * @throws IOException on I/O error
     */
    @Nullable
    InputStream getInputStream() throws IOException;

    /**
     * Returns an output stream.
     *
     * <p>
     * This is the only way to send data to the communications port.
     * If the port is unidirectional and doesn't support sending data, then getOutputStream returns null.
     *
     * @return the output stream or null
     * @throws IOException on I/O error
     */
    @Nullable
    OutputStream getOutputStream() throws IOException;

    /**
     * Retrieves the name of the serial port.
     *
     * @return the name of the serial port
     */
    String getName();

    /**
     * Registers a {@link SerialPortEventListener} object to listen for {@link SerialPortEvent}s.
     *
     * <p>
     * Interest in specific events may be expressed using the notifyOnXXX calls.
     * The serialEvent method of SerialPortEventListener will be called with a SerialEvent object describing the event.
     *
     * Only one listener per SerialPort is allowed.
     * Once a listener is registered, subsequent call attempts to addEventListener will throw a
     * TooManyListenersException without effecting the listener already registered.
     *
     * <p>
     * All the events received by this listener are generated by one dedicated thread that belongs to the SerialPort
     * object.
     * After the port is closed, no more event will be generated.
     *
     * @param listener the listener
     * @throws TooManyListenersException if too many listeners has been added
     */
    void addEventListener(SerialPortEventListener listener) throws TooManyListenersException;

    /**
     * Remove the event listener.
     */
    void removeEventListener();

    /**
     * Enable / disable the notification for 'data available'.
     *
     * @param enable true if the notification should be enabled
     */
    void notifyOnDataAvailable(boolean enable);

    /**
     * Enable / disable the notification on break interrupt.
     *
     * @param enable true if the notification should be enabled
     */
    void notifyOnBreakInterrupt(boolean enable);

    /**
     * Enable / disable the notification on framing error.
     *
     * @param enable true if the notification should be enabled
     */
    void notifyOnFramingError(boolean enable);

    /**
     * Enable / disable the notification on overrun error.
     *
     * @param enable true if the notification should be enabled
     */
    void notifyOnOverrunError(boolean enable);

    /**
     * Enable / disable the notification on parity error.
     *
     * @param enable true if the notification should be enabled
     */
    void notifyOnParityError(boolean enable);

    /**
     * Enable / disable the notification on output buffer empty.
     *
     * @param enable true if the notification should be enabled
     */
    void notifyOnOutputEmpty(boolean enable);

    /**
     * Enable / disable the notification on CTS.
     *
     * @param enable true if the notification should be enabled
     */
    void notifyOnCTS(boolean enable);

    /**
     * Enable / disable the notification on DSR.
     *
     * @param enable true if the notification should be enabled
     */
    void notifyOnDSR(boolean enable);

    /**
     * Enable / disable the notification on ring indicator.
     *
     * @param enable true if the notification should be enabled
     */
    void notifyOnRingIndicator(boolean enable);

    /**
     * Enable / disable the notification on carrier detect.
     *
     * @param enable true if the notification should be enabled
     */
    void notifyOnCarrierDetect(boolean enable);

    /**
     * Enables the receive timeout.
     *
     * <p>
     * When the receive timeout condition becomes true, a read from the input stream for this port will return
     * immediately.
     *
     * @param timeout the timeout (milliseconds), must be greater or equal to zero
     * @throws UnsupportedCommOperationException if the operation is not supported
     * @throws IllegalArgumentException on a negative timeout value
     */
    void enableReceiveTimeout(int timeout) throws UnsupportedCommOperationException, IllegalArgumentException;

    /**
     * Disable receive timeout.
     */
    void disableReceiveTimeout();

    /**
     * Sets the flow control mode value.
     *
     * @param flowcontrolRtsctsOut The flowcontrol (<code>int</code>) parameter.
     * @throws UnsupportedCommOperationException Unsupported Comm Operation Exception.
     */
    void setFlowControlMode(int flowcontrolRtsctsOut) throws UnsupportedCommOperationException;

    /**
     * Gets the flow control mode value.
     *
     * @return flowcontrol value.
     */
    int getFlowControlMode();

    /**
     * Enable receive threshold with the specified thresh parameter.
     *
     * @param i The thresh (<code>int</code>) parameter.
     * @throws UnsupportedCommOperationException Unsupported Comm Operation Exception.
     */
    void enableReceiveThreshold(int i) throws UnsupportedCommOperationException;

    /**
     * Sets or clears the RTS (Request To Send) bit in the UART, if supported by the underlying implementation.
     *
     * @param rts true rts is set, false if rts cleared
     */
    void setRTS(boolean rts);

    /**
     * Check current state of RTS (Request To Send).
     *
     * @return true if RTS is set, otherwise false
     */
    boolean isRTS();

    /**
     * Sets or clears the DTR (Request To Send) bit in the UART, if supported by the underlying implementation.
     *
     * @param state true DTR is set, false if DTR cleared
     */
    void setDTR(boolean state);

    /**
     * Check current state of DTR (Data Terminal Ready).
     *
     * @return true if DTR is set, otherwise false
     */
    boolean isDTR();

    /**
     * Check current state of CTS (Clear To Send).
     *
     * @return true if CTS is set, otherwise false
     */
    boolean isCTS();

    /**
     * Check current state of DSR (Request To Send).
     *
     * @return true if DSR is set, otherwise false
     */
    boolean isDSR();

    /**
     * Check current state of CD (Carrier Detect).
     *
     * @return true if CD is set, otherwise false
     */
    boolean isCD();

    /**
     * Check current state of RI (Ring Indicator).
     *
     * @return true if RI is set, otherwise false
     */
    boolean isRI();

    /**
     * Send break.
     *
     * @param duration Break duration parameter
     */
    void sendBreak(int duration);
}
