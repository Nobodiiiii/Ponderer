package com.nododiiiii.ponderer.feedback;

import net.minecraft.network.chat.Component;

/** A screen's existing status area, shared by local and asynchronous operations. */
public interface FeedbackTarget {
    void acceptFeedback(Component message, FeedbackLevel level);
}
