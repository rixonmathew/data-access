package com.rixon.learn.spring.data.arrow.model;

/**
 * The two Arrow IPC framings. STREAM is a sequence of messages read front to back (what Flight sends);
 * FILE adds a footer with batch offsets so batches can be read in any order.
 */
public enum IpcFormat { STREAM, FILE }
