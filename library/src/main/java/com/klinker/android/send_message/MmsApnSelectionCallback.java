package com.klinker.android.send_message;

/**
 * Result callbacks for MMS APN resolution.
 * The host app shows its own dialog and calls {@link MmsApnSelectionActions} on OK / cancel.
 */
public interface MmsApnSelectionCallback {

    /**
     * APN is already known. No dialog is required.
     */
    void onApnReady(MmsApn apn);

    /**
     * Multiple MMS configurations exist. Show a host dialog using {@link MmsApnDialogContent}
     * and wire OK / cancel to {@code actions}.
     */
    void onApnChoiceRequired(MmsApnDialogContent content, MmsApnSelectionActions actions);

    /**
     * User dismissed the host dialog without choosing an APN.
     */
    void onApnSelectionCancelled();

    /**
     * No MMS configuration could be resolved for the selected SIM.
     */
    void onApnUnavailable();
}