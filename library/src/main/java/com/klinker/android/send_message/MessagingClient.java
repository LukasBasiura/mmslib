package com.klinker.android.send_message;

import android.content.Context;
import android.os.Parcelable;

import java.util.List;

/**
 * Entry point for host apps such as MessageMate.
 * Keeps SIM selection and MMS APN configuration explicit per send operation.
 */
public class MessagingClient {

    private final Context context;
    private final Settings baseSettings;

    public MessagingClient(Context context) {
        this(context, Utils.getDefaultSendSettings(context));
    }

    public MessagingClient(Context context, Settings baseSettings) {
        this.context = context.getApplicationContext();
        this.baseSettings = new Settings(baseSettings);
    }

    /**
     * Returns MMS APN options for the given SIM. The host app should present these in its own UI.
     */
    public List<MmsApn> getAvailableMmsApns(int subscriptionId) {
        return MmsApnResolver.loadAvailableApns(context, subscriptionId);
    }

    /**
     * Persists the user's MMS APN choice for a specific SIM.
     */
    public void saveMmsApn(int subscriptionId, MmsApn apn) {
        MmsApnResolver.saveSelectedApn(context, subscriptionId, apn);
    }

    /**
     * Builds send settings for a specific SIM and optional MMS APN override.
     */
    public Settings buildSettings(int subscriptionId, MmsApn mmsApn) {
        final Settings settings = new Settings(baseSettings);
        settings.setSubscriptionId(subscriptionId);
        if (mmsApn != null) {
            MmsApnResolver.applyToSettings(settings, mmsApn);
        } else {
            final MmsApn preferred = MmsApnResolver.loadPreferredApn(context, subscriptionId);
            if (preferred != null) {
                MmsApnResolver.applyToSettings(settings, preferred);
            }
        }
        return settings;
    }

    /**
     * Sends a message using the selected SIM. For MMS, pass a resolved {@link MmsApn} when
     * multiple carrier configurations are available.
     */
    public void send(
            Message message,
            int subscriptionId,
            MmsApn mmsApn,
            Parcelable sentParcelable,
            Parcelable deliveredParcelable
    ) throws Exception {
        final Settings settings = buildSettings(subscriptionId, mmsApn);
        final Transaction transaction = new Transaction(context, settings);
        transaction.sendNewMessage(message, sentParcelable, deliveredParcelable);
    }

    public void send(Message message, int subscriptionId) throws Exception {
        send(message, subscriptionId, null, null, null);
    }

    /**
     * Resolves MMS APN for the selected SIM and invokes host callbacks.
     * The host app renders its own translated dialog when multiple APNs are available.
     */
    public void resolveMmsApn(int subscriptionId, MmsApnSelectionCallback callback) {
        MmsApnSelection.resolve(context, subscriptionId, callback);
    }

    /**
     * Returns dialog content only. Useful to prefetch data before showing host UI.
     */
    public MmsApnDialogContent getMmsApnDialogContent(int subscriptionId) {
        return MmsApnSelection.buildDialogContent(context, subscriptionId);
    }
}