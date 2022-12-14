// Copyright 2019 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.base.compat;

import android.os.Build;
import android.view.Window;

import androidx.annotation.RequiresApi;

/**
 * Utility class to use new APIs that were added in O_MR1 (API level 27). These need to exist in a
 * separate class so that Android framework can successfully verify classes without
 * encountering the new APIs.
 */
@RequiresApi(Build.VERSION_CODES.O_MR1)
public final class ApiHelperForOMR1 {
    private ApiHelperForOMR1() {}

    /** See {@link Window#isWideColorGamut() }. */
    public static boolean isWideColorGamut(Window window) {
        return window.isWideColorGamut();
    }
}
