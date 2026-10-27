package com.nododiiiii.ponderer.ai;

import java.util.ArrayList;
import java.util.List;

/** A bounded inbox; accepting a submission and closing the inbox is one atomic decision. */
public final class AiRunControl {
    private final List<String> instructions = new ArrayList<>();
    private int delivered;
    private boolean closed;

    public synchronized boolean steer(String instruction) {
        if (closed || instruction == null || instruction.isBlank() || instruction.length() > 2048 || instructions.size() >= 8) return false;
        instructions.add(instruction.trim());
        return true;
    }

    synchronized List<String> drain() {
        List<String> pending = List.copyOf(instructions.subList(delivered, instructions.size()));
        delivered = instructions.size();
        return pending;
    }

    synchronized List<String> instructions() { return List.copyOf(instructions); }
    synchronized boolean settle() {
        if (delivered != instructions.size()) return false;
        closed = true;
        return true;
    }
    public synchronized void close() { closed = true; }
}
