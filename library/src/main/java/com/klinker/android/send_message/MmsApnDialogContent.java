package com.klinker.android.send_message;

import java.util.Collections;
import java.util.List;

/**
 * Data required to render an MMS APN picker in the host application.
 * Contains no UI strings — MessageMate supplies all translated copy.
 */
public final class MmsApnDialogContent {

    private final int subscriptionId;
    private final String simDisplayName;
    private final int simSlotIndex;
    private final List<MmsApnOption> options;
    private final int preselectedIndex;

    public MmsApnDialogContent(
            int subscriptionId,
            String simDisplayName,
            int simSlotIndex,
            List<MmsApnOption> options,
            int preselectedIndex) {
        this.subscriptionId = subscriptionId;
        this.simDisplayName = simDisplayName != null ? simDisplayName : "";
        this.simSlotIndex = simSlotIndex;
        this.options = options != null ? options : Collections.<MmsApnOption>emptyList();
        this.preselectedIndex = preselectedIndex;
    }

    public int getSubscriptionId() {
        return subscriptionId;
    }

    /**
     * SIM label from the system, e.g. "Orange PL". Host app may use it in a translated template.
     */
    public String getSimDisplayName() {
        return simDisplayName;
    }

    /**
     * Zero-based SIM slot when available, otherwise -1.
     */
    public int getSimSlotIndex() {
        return simSlotIndex;
    }

    public List<MmsApnOption> getOptions() {
        return options;
    }

    public int getOptionCount() {
        return options.size();
    }

    public MmsApnOption getOption(int index) {
        return options.get(index);
    }

    /**
     * Suggested default row, usually the previously saved APN for this SIM.
     */
    public int getPreselectedIndex() {
        return preselectedIndex;
    }

    public boolean hasValidPreselection() {
        return preselectedIndex >= 0 && preselectedIndex < options.size();
    }
}