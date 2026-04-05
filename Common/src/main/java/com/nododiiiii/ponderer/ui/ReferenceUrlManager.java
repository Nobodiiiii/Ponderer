package com.nododiiiii.ponderer.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Manages URLs for the AI generate screen, including auto-added URLs
 * and their relationship with selected items.
 */
public class ReferenceUrlManager implements SnapshotParticipant {
    public record ReferenceUrl(String url, boolean isAutoAdded) {
    }

    private final DynamicListState<ReferenceUrl> referenceUrls = new DynamicListState<>(
        "reference_url",
        new DynamicListState.ItemCodec<>() {
            @Override
            public void snapshot(String prefix, ReferenceUrl item, Map<String, String> snapshot) {
                snapshot.put(prefix + "_value", item.url());
                snapshot.put(prefix + "_auto", String.valueOf(item.isAutoAdded()));
            }

            @Override
            public ReferenceUrl restore(String prefix, Map<String, String> snapshot) {
                return new ReferenceUrl(
                    snapshot.getOrDefault(prefix + "_value", ""),
                    Boolean.parseBoolean(snapshot.getOrDefault(prefix + "_auto", "false")));
            }
        },
        () -> new ReferenceUrl("", false),
        0);

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
        List<ReferenceUrl> values = new ArrayList<>(referenceUrls.items());
        values.add(0, new ReferenceUrl(url, isAutoAdded));
        referenceUrls.replaceAll(values);
    }

    /**
     * Remove all auto-added URLs for the current item.
     */
    public void removeAutoUrlsForItem() {
        // Remove existing auto-added URLs
        List<ReferenceUrl> filtered = new ArrayList<>();
        for (ReferenceUrl value : referenceUrls.items()) {
            if (!value.isAutoAdded()) {
                filtered.add(value);
            }
        }
        referenceUrls.replaceAll(filtered);
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
        for (ReferenceUrl refUrl : referenceUrls.items()) {
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
        for (ReferenceUrl refUrl : referenceUrls.items()) {
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

    public void replaceWith(List<String> urls, List<Boolean> autoAddedFlags) {
        List<ReferenceUrl> values = new ArrayList<>();
        for (int i = 0; i < urls.size(); i++) {
            boolean isAutoAdded = i < autoAddedFlags.size() && autoAddedFlags.get(i);
            values.add(new ReferenceUrl(urls.get(i), isAutoAdded));
        }
        referenceUrls.replaceAll(values);
    }

    @Override
    public void snapshot(Map<String, String> snapshot) {
        referenceUrls.snapshot(snapshot);
    }

    @Override
    public void restore(Map<String, String> snapshot) {
        referenceUrls.restore(snapshot);
    }
}
