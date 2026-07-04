package com.klinker.android.send_message;

import android.content.Context;
import android.os.Build;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves MMS APN configuration without showing any library UI.
 *
 * Typical MessageMate flow:
 * <pre>
 * MmsApnSelection.resolve(context, simId, new MmsApnSelectionCallback() {
 *     public void onApnReady(MmsApn apn) { send(apn); }
 *
 *     public void onApnChoiceRequired(MmsApnDialogContent content, MmsApnSelectionActions actions) {
 *         showMyTranslatedDialog(
 *             title = getString(R.string.mms_apn_title),
 *             items = content.getOptions().map { it.primaryLabel },
 *             onOk = { actions.confirm(selectedIndex) },
 *             onCancel = { actions.cancel() }
 *         );
 *     }
 *
 *     public void onApnSelectionCancelled() { ... }
 *     public void onApnUnavailable() { ... }
 * });
 * </pre>
 */
public final class MmsApnSelection {

    private MmsApnSelection() {
    }

    public static void resolve(Context context, int subscriptionId, MmsApnSelectionCallback callback) {
        final Context appContext = context.getApplicationContext();
        final int subId = Utils.resolveSubscriptionId(subscriptionId);

        final MmsApn saved = MmsApnResolver.getSavedApn(appContext, subId);
        if (saved != null && saved.isValid()) {
            callback.onApnReady(saved);
            return;
        }

        final List<MmsApn> available = MmsApnResolver.loadAvailableApns(appContext, subId);
        if (available.isEmpty()) {
            callback.onApnUnavailable();
            return;
        }

        if (available.size() == 1) {
            final MmsApn onlyOption = available.get(0);
            MmsApnResolver.saveSelectedApn(appContext, subId, onlyOption);
            callback.onApnReady(onlyOption);
            return;
        }

        final MmsApnDialogContent content = buildDialogContent(appContext, subId, available, saved);
        final MmsApnSelectionActions actions = createActions(appContext, subId, content, callback);
        callback.onApnChoiceRequired(content, actions);
    }

    /**
     * Builds dialog content without triggering callbacks.
     * Useful when the host app wants to prefetch data before showing UI.
     */
    public static MmsApnDialogContent buildDialogContent(Context context, int subscriptionId) {
        final int subId = Utils.resolveSubscriptionId(subscriptionId);
        final List<MmsApn> available = MmsApnResolver.loadAvailableApns(context, subId);
        final MmsApn saved = MmsApnResolver.getSavedApn(context, subId);
        return buildDialogContent(context, subId, available, saved);
    }

    private static MmsApnDialogContent buildDialogContent(
            Context context,
            int subId,
            List<MmsApn> available,
            MmsApn saved) {
        final List<MmsApnOption> options = new ArrayList<>();
        int preselectedIndex = -1;

        for (int i = 0; i < available.size(); i++) {
            final MmsApn apn = available.get(i);
            options.add(new MmsApnOption(i, apn));
            if (saved != null && matches(saved, apn)) {
                preselectedIndex = i;
            }
        }

        if (preselectedIndex < 0 && !options.isEmpty()) {
            preselectedIndex = 0;
        }

        final SimInfo simInfo = loadSimInfo(context, subId);
        return new MmsApnDialogContent(
                subId,
                simInfo.displayName,
                simInfo.slotIndex,
                options,
                preselectedIndex);
    }

    private static MmsApnSelectionActions createActions(
            final Context context,
            final int subId,
            final MmsApnDialogContent content,
            final MmsApnSelectionCallback callback) {
        return new MmsApnSelectionActions() {
            @Override
            public void confirm(int selectedIndex) {
                if (selectedIndex < 0 || selectedIndex >= content.getOptionCount()) {
                    callback.onApnUnavailable();
                    return;
                }
                confirm(content.getOption(selectedIndex).getApn());
            }

            @Override
            public void confirm(MmsApn apn) {
                if (apn == null || !apn.isValid()) {
                    callback.onApnUnavailable();
                    return;
                }
                MmsApnResolver.saveSelectedApn(context, subId, apn);
                callback.onApnReady(apn);
            }

            @Override
            public void cancel() {
                callback.onApnSelectionCancelled();
            }
        };
    }

    private static boolean matches(MmsApn left, MmsApn right) {
        return left.getMmsc().equals(right.getMmsc())
                && left.getProxy().equals(right.getProxy())
                && left.getPort().equals(right.getPort());
    }

    private static SimInfo loadSimInfo(Context context, int subId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            final SubscriptionManager subscriptionManager = SubscriptionManager.from(context);
            final SubscriptionInfo info = subscriptionManager.getActiveSubscriptionInfo(subId);
            if (info != null) {
                final CharSequence displayName = info.getDisplayName();
                return new SimInfo(
                        displayName != null ? displayName.toString() : "",
                        info.getSimSlotIndex());
            }
        }
        return new SimInfo("", -1);
    }

    private static final class SimInfo {
        final String displayName;
        final int slotIndex;

        SimInfo(String displayName, int slotIndex) {
            this.displayName = displayName;
            this.slotIndex = slotIndex;
        }
    }
}