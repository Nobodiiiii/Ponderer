package com.nododiiiii.ponderer.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages URLs for the AI generate screen, including auto-added URLs
 * and their relationship with selected items.
 */
public class ReferenceUrlManager {
    public record ReferenceUrl(String url, boolean isAutoAdded) {
    }

    private final List<ReferenceUrl> referenceUrls = new ArrayList<>();

    /**
     * Add a URL to the manager.
     * 
     * @param url         The URL to add
     * @param itemId      The item identifier associated with this URL
     * @param isAutoAdded Whether this URL is auto-added (non-editable)
     */
    public void addUrl(String url, String itemId, boolean isAutoAdded) {
        // Remove existing auto-added URLs for the previous item
        removeAutoUrlsForItem();
        // Add URL to the beginning of the list
        referenceUrls.add(0, new ReferenceUrl(url, isAutoAdded));
    }

    /**
     * Remove all auto-added URLs for the current item.
     */
    public void removeAutoUrlsForItem() {
        // Remove existing auto-added URLs
        for (int i = referenceUrls.size() - 1; i >= 0; i--) {
            if (referenceUrls.get(i).isAutoAdded()) {
                referenceUrls.remove(i);
            }
        }
    }

    /**
     * Remove a URL at the specified index.
     * 
     * @param index The index of the URL to remove
     */
    public void removeUrl(int index) {
        if (index >= 0 && index < referenceUrls.size()) {
            referenceUrls.remove(index);
        }
    }

    /**
     * Add a manually added URL.
     * 
     * @param url The URL to add
     */
    public void addManualUrl(String url) {
        referenceUrls.add(new ReferenceUrl(url, false));
    }

    /**
     * Update a URL at the specified index.
     * 
     * @param index The index of the URL to update
     * @param url   The new URL value
     */
    public void updateUrl(int index, String url) {
        if (index >= 0 && index < referenceUrls.size()) {
            ReferenceUrl oldUrl = referenceUrls.get(index);
            referenceUrls.set(index, new ReferenceUrl(url, oldUrl.isAutoAdded()));
        }
    }

    /**
     * Get the list of URL values.
     * 
     * @return The list of URL values
     */
    public List<String> getUrlValues() {
        List<String> urlValues = new ArrayList<>();
        for (ReferenceUrl refUrl : referenceUrls) {
            urlValues.add(refUrl.url());
        }
        return urlValues;
    }

    /**
     * Get the list indicating whether each URL is auto-added.
     * 
     * @return The list of auto-added flags
     */
    public List<Boolean> getUrlAutoAdded() {
        List<Boolean> autoAddedFlags = new ArrayList<>();
        for (ReferenceUrl refUrl : referenceUrls) {
            autoAddedFlags.add(refUrl.isAutoAdded());
        }
        return autoAddedFlags;
    }

    /**
     * Clear all URLs.
     */
    public void clear() {
        referenceUrls.clear();
    }
}