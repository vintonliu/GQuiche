// Copyright 2019 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.base.library_loader;

import android.content.pm.ApplicationInfo;

import androidx.test.filters.SmallTest;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.chromium.base.JniException;
import org.chromium.base.NativeLibraryLoadedStatus;
import org.chromium.base.annotations.JNINamespace;
import org.chromium.base.annotations.NativeMethods;
import org.chromium.base.test.BaseJUnit4ClassRunner;
import org.chromium.base.test.util.Batch;
import org.chromium.base.test.util.CallbackHelper;
import org.chromium.base.test.util.RequiresRestart;
import org.chromium.build.BuildConfig;
import org.chromium.build.annotations.MainDex;

import java.util.concurrent.TimeoutException;

/**
 * Tests for early JNI initialization.
 */
@RunWith(BaseJUnit4ClassRunner.class)
@JNINamespace("base")
@MainDex
@Batch(Batch.UNIT_TESTS)
public class EarlyNativeTest {
    private boolean mWasInitialized;
    private CallbackHelper mLoadMainDexStarted;
    private CallbackHelper mEnsureMainDexInitializedFinished;

    @Before
    public void setUp() {
        mWasInitialized = LibraryLoader.getInstance().isInitialized();
        LibraryLoader.getInstance().resetForTesting();
        mLoadMainDexStarted = new CallbackHelper();
        mEnsureMainDexInitializedFinished = new CallbackHelper();
    }

    @After
    public void tearDown() {
        // Restore the simulated library state (due to the resetForTesting() call).
        if (mWasInitialized) {
            LibraryLoader.getInstance().ensureInitialized();
        }
    }

    private class TestLibraryLoader extends LibraryLoader {
        @Override
        @SuppressWarnings("GuardedBy") // ErrorProne: should be guarded by 'mLock'
        protected void loadMainDexAlreadyLocked(ApplicationInfo appInfo, boolean inZygote) {
            mLoadMainDexStarted.notifyCalled();
            super.loadMainDexAlreadyLocked(appInfo, inZygote);
        }

        @Override
        protected void loadNonMainDex() {
            try {
                mEnsureMainDexInitializedFinished.waitForCallback(0);
            } catch (TimeoutException e) {
                throw new RuntimeException(e);
            }
            super.loadNonMainDex();
        }
    }

    @NativeMethods
    interface Natives {
        boolean isCommandLineInitialized();
        boolean isProcessNameEmpty();
    }

    @Test
    @SmallTest
    public void testEnsureMainDexInitialized() {
        LibraryLoader.getInstance().ensureMainDexInitialized();
        // Some checks to ensure initialization has taken place.
        Assert.assertTrue(EarlyNativeTestJni.get().isCommandLineInitialized());
        Assert.assertFalse(EarlyNativeTestJni.get().isProcessNameEmpty());

        // Make sure the Native library isn't considered ready for general use.
        Assert.assertFalse(LibraryLoader.getInstance().isInitialized());

        LibraryLoader.getInstance().ensureInitialized();
        Assert.assertTrue(LibraryLoader.getInstance().isInitialized());

        // Test resetForTesting().
        LibraryLoader.getInstance().resetForTesting();
        Assert.assertFalse(LibraryLoader.getInstance().isInitialized());
        LibraryLoader.getInstance().ensureInitialized();
        Assert.assertTrue(LibraryLoader.getInstance().isInitialized());
    }

    private void doTestFullInitializationDoesntBlockMainDexInitialization(final boolean initialize)
            throws Exception {
        final TestLibraryLoader loader = new TestLibraryLoader();
        loader.setLibraryProcessType(LibraryProcessType.PROCESS_BROWSER);
        final Thread t1 = new Thread(() -> {
            if (initialize) {
                loader.ensureInitialized();
            } else {
                loader.loadNow();
            }
        });
        t1.start();
        mLoadMainDexStarted.waitForCallback(0);
        final Thread t2 = new Thread(() -> {
            loader.ensureMainDexInitialized();
            Assert.assertFalse(loader.isInitialized());
            mEnsureMainDexInitializedFinished.notifyCalled();
        });
        t2.start();
        t2.join();
        t1.join();
        Assert.assertTrue(loader.isInitialized());
    }

    @Test
    @SmallTest
    @RequiresRestart("Uses custom LibraryLoader")
    public void testFullInitializationDoesntBlockMainDexInitialization() throws Exception {
        doTestFullInitializationDoesntBlockMainDexInitialization(true);
    }

    @Test
    @SmallTest
    @RequiresRestart("Uses custom LibraryLoader")
    public void testLoadDoesntBlockMainDexInitialization() throws Exception {
        doTestFullInitializationDoesntBlockMainDexInitialization(false);
    }

    @Test
    @SmallTest
    public void testNativeMethodsReadyAfterLibraryInitialized() {
        // Test is a no-op if DCHECK isn't on.
        if (!BuildConfig.ENABLE_ASSERTS) return;

        Assert.assertFalse(
                NativeLibraryLoadedStatus.getProviderForTesting().areMainDexNativeMethodsReady());
        Assert.assertFalse(
                NativeLibraryLoadedStatus.getProviderForTesting().areNativeMethodsReady());

        LibraryLoader.getInstance().ensureMainDexInitialized();
        Assert.assertTrue(
                NativeLibraryLoadedStatus.getProviderForTesting().areMainDexNativeMethodsReady());
        Assert.assertFalse(
                NativeLibraryLoadedStatus.getProviderForTesting().areNativeMethodsReady());

        LibraryLoader.getInstance().ensureInitialized();
        Assert.assertTrue(
                NativeLibraryLoadedStatus.getProviderForTesting().areMainDexNativeMethodsReady());
        Assert.assertTrue(
                NativeLibraryLoadedStatus.getProviderForTesting().areNativeMethodsReady());
    }

    @Test
    @SmallTest
    public void testNativeMethodsNotReadyThrows() {
        // Test is a no-op if dcheck isn't on.
        if (!BuildConfig.ENABLE_ASSERTS) return;

        try {
            EarlyNativeTestJni.get().isCommandLineInitialized();
            Assert.fail("Using JNI before the library is loaded should throw an exception.");
        } catch (JniException e) {
        }
    }
}
