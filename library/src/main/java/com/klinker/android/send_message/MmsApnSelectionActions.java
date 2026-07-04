package com.klinker.android.send_message;

/**
 * OK / cancel handlers returned to the host app while an MMS APN dialog is visible.
 */
public interface MmsApnSelectionActions {

    /**
     * Call when the user confirms a choice in the host dialog.
     *
     * @param selectedIndex index from {@link MmsApnDialogContent#getOptions()}
     */
    void confirm(int selectedIndex);

    /**
     * Call when the user confirms a choice in the host dialog.
     */
    void confirm(MmsApn apn);

    /**
     * Call when the user cancels the host dialog.
     */
    void cancel();
}