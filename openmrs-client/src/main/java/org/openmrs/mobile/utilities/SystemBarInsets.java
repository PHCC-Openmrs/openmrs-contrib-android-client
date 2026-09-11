/*
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.mobile.utilities;

import android.app.Activity;
import android.content.res.Configuration;
import android.view.View;
import android.view.Window;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * Edge-to-edge support for this app's View-based screens.
 *
 * <p>From targetSdk 35 Android draws every window edge-to-edge, and from targetSdk 36 the
 * {@code windowOptOutEdgeToEdgeEnforcement} escape hatch is disabled, so there is no way to keep
 * the old inset-by-default behaviour. This app has no per-screen inset handling at all, so without
 * something like this every screen would draw underneath the status and navigation bars.
 *
 * <p>The approach is deliberately global and minimal: pad the decor content root by the
 * system-bar, cutout and IME insets. That reproduces the pre-edge-to-edge layout for all ~36
 * activities from one place.
 *
 * <p>Two details matter:
 * <ul>
 *   <li>The padding target is appcompat's {@code action_bar_root} when it exists, not
 *       {@code android.R.id.content}. The support ActionBar lives <em>above</em> the content frame,
 *       so padding only the content frame would still leave the ActionBar under the status bar.</li>
 *   <li>IME insets are folded into the bottom inset. Once a window is edge-to-edge,
 *       {@code adjustResize} no longer shrinks it, so screens that rely on it
 *       (LoginActivity, AddEditProviderActivity) would otherwise have the keyboard cover
 *       their fields.</li>
 * </ul>
 *
 * <p>Known cosmetic change: the bars are now transparent and show the window background rather
 * than the theme's {@code colorPrimaryDark}. Bar icon colours are set to match the current
 * day/night background so they stay legible. Painting the bar areas per screen (for example
 * extending the app-bar colour behind the status bar) is follow-up polish, not a correctness issue.
 */
public final class SystemBarInsets {

    private SystemBarInsets() {
    }

    /**
     * Pads the decor content root by the system bar, display cutout and IME insets.
     * Safe to call more than once per activity, and safe to call before a content view is set
     * (it simply does nothing in that case).
     */
    public static void apply(Activity activity) {
        if (activity == null) {
            return;
        }
        final View root = findPaddingTarget(activity);
        if (root == null) {
            return;
        }
        setBarIconsForBackground(activity);

        // Preserve whatever padding the view already had; insets are added on top of it.
        final int baseLeft = root.getPaddingLeft();
        final int baseTop = root.getPaddingTop();
        final int baseRight = root.getPaddingRight();
        final int baseBottom = root.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            view.setPadding(
                    baseLeft + bars.left,
                    baseTop + bars.top,
                    baseRight + bars.right,
                    baseBottom + Math.max(bars.bottom, ime.bottom));
            // Nothing below this point handles insets itself, and the two layouts that set
            // android:fitsSystemWindows on their AppBarLayout are already covered by the padding
            // above, so stop propagation rather than letting children inset a second time.
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(root);
    }

    private static View findPaddingTarget(Activity activity) {
        View root = activity.findViewById(androidx.appcompat.R.id.action_bar_root);
        if (root == null) {
            root = activity.findViewById(android.R.id.content);
        }
        return root;
    }

    /**
     * The bars are transparent now, so their icons sit on the window background. These themes are
     * DayNight, so pick the icon tint from the current ui mode: dark icons on the light
     * background, light icons on the dark one.
     */
    private static void setBarIconsForBackground(Activity activity) {
        final Window window = activity.getWindow();
        if (window == null) {
            return;
        }
        final int uiMode = activity.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        final boolean night = uiMode == Configuration.UI_MODE_NIGHT_YES;

        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(!night);
        controller.setAppearanceLightNavigationBars(!night);
    }
}
