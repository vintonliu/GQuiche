// Copyright 2012 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.base;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.provider.MediaStore;
import android.system.Os;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.compat.ApiHelperForM;
import org.chromium.base.compat.ApiHelperForQ;
import org.chromium.base.compat.ApiHelperForR;
import org.chromium.base.task.AsyncTask;
import org.chromium.build.annotations.MainDex;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class provides the path related methods for the native library.
 */
@MainDex
public abstract class PathUtils {
    private static final String TAG = "PathUtils";
    private static final String THUMBNAIL_DIRECTORY_NAME = "textures";

    private static final int DATA_DIRECTORY = 0;
    private static final int THUMBNAIL_DIRECTORY = 1;
    private static final int CACHE_DIRECTORY = 2;
    private static final int NUM_DIRECTORIES = 3;
    private static final AtomicBoolean sInitializationStarted = new AtomicBoolean();
    private static FutureTask<String[]> sDirPathFetchTask;

    // If the FutureTask started in setPrivateDataDirectorySuffix() fails to complete by the time we
    // need the values, we will need the suffix so that we can restart the task synchronously on
    // the UI thread.
    private static String sDataDirectorySuffix;
    private static String sCacheSubDirectory;

    // Prevent instantiation.
    private PathUtils() {}

    // Resetting is useful in Robolectric tests, where each test is run with a different
    // data directory.
    public static void resetForTesting() {
        sInitializationStarted.set(false);
        sDirPathFetchTask = null;
        sDataDirectorySuffix = null;
        sCacheSubDirectory = null;
    }

    /**
     * Get the directory paths from sDirPathFetchTask if available, or compute it synchronously
     * on the UI thread otherwise. This should only be called as part of Holder's initialization
     * above to guarantee thread-safety as part of the initialization-on-demand holder idiom.
     */
    private static String[] getOrComputeDirectoryPaths() {
        if (!sDirPathFetchTask.isDone()) {
            try (StrictModeContext ignored = StrictModeContext.allowDiskWrites()) {
                // No-op if already ran.
                sDirPathFetchTask.run();
            }
        }
        try {
            return sDirPathFetchTask.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressLint("NewApi")
    private static void chmod(String path, int mode) {
        // Both Os.chmod and ErrnoException require SDK >= 21. But while Dalvik on < 21 tolerates
        // Os.chmod, it throws VerifyError for ErrnoException, so catch Exception instead.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return;
        try {
            Os.chmod(path, mode);
        } catch (Exception e) {
            Log.e(TAG, "Failed to set permissions for path \"" + path + "\"");
        }
    }

    /**
     * Fetch the path of the directory where private data is to be stored by the application. This
     * is meant to be called in an FutureTask in setPrivateDataDirectorySuffix(), but if we need the
     * result before the FutureTask has had a chance to finish, then it's best to cancel the task
     * and run it on the UI thread instead, inside getOrComputeDirectoryPaths().
     *
     * @see Context#getDir(String, int)
     */
    private static String[] setPrivateDataDirectorySuffixInternal() {
        String[] paths = new String[NUM_DIRECTORIES];
        Context appContext = ContextUtils.getApplicationContext();
        paths[DATA_DIRECTORY] = appContext.getDir(
                sDataDirectorySuffix, Context.MODE_PRIVATE).getPath();
        // MODE_PRIVATE results in rwxrwx--x, but we want rwx------, as a defence-in-depth measure.
        chmod(paths[DATA_DIRECTORY], 0700);
        paths[THUMBNAIL_DIRECTORY] = appContext.getDir(
                THUMBNAIL_DIRECTORY_NAME, Context.MODE_PRIVATE).getPath();
        if (appContext.getCacheDir() != null) {
            if (sCacheSubDirectory == null) {
                paths[CACHE_DIRECTORY] = appContext.getCacheDir().getPath();
            } else {
                File cacheDir = new File(appContext.getCacheDir(), sCacheSubDirectory);
                cacheDir.mkdir();
                paths[CACHE_DIRECTORY] = cacheDir.getPath();
                // Set to rwx--S--- as the Android cache dir has a distinct gid and is setgid.
                chmod(paths[CACHE_DIRECTORY], 02700);
            }
        }
        return paths;
    }

    /**
     * Starts an asynchronous task to fetch the path of the directory where private data is to be
     * stored by the application.
     *
     * <p>This task can run long (or more likely be delayed in a large task queue), in which case we
     * want to cancel it and run on the UI thread instead. Unfortunately, this means keeping a bit
     * of extra static state - we need to store the suffix and the application context in case we
     * need to try to re-execute later.
     *
     * @param suffix The private data directory suffix.
     * @param cacheSubDir The subdirectory in the cache directory to use, if non-null.
     * @see Context#getDir(String, int)
     */
    public static void setPrivateDataDirectorySuffix(String suffix, String cacheSubDir) {
        // This method should only be called once, but many tests end up calling it multiple times,
        // so adding a guard here.
        if (!sInitializationStarted.getAndSet(true)) {
            assert ContextUtils.getApplicationContext() != null;
            sDataDirectorySuffix = suffix;
            sCacheSubDirectory = cacheSubDir;

            // We don't use an AsyncTask because this function is called in early Webview startup
            // and it won't always have a UI thread available. Thus, we can't use AsyncTask which
            // inherently posts to the UI thread for onPostExecute().
            sDirPathFetchTask = new FutureTask<>(PathUtils::setPrivateDataDirectorySuffixInternal);
            AsyncTask.THREAD_POOL_EXECUTOR.execute(sDirPathFetchTask);
        } else {
            assert TextUtils.equals(sDataDirectorySuffix, suffix)
                : String.format("%s != %s", suffix, sDataDirectorySuffix);
            assert TextUtils.equals(sCacheSubDirectory, cacheSubDir)
                : String.format("%s != %s", cacheSubDir, sCacheSubDirectory);
        }
    }

    public static void setPrivateDataDirectorySuffix(String suffix) {
        setPrivateDataDirectorySuffix(suffix, null);
    }

    /**
     * @param index The index of the cached directory path.
     * @return The directory path requested.
     */
    private static String getDirectoryPath(int index) {
        String[] paths = getOrComputeDirectoryPaths();
        return paths[index];
    }

    /**
     * @return the private directory that is used to store application data.
     */
    @CalledByNative
    public static String getDataDirectory() {
        assert sDirPathFetchTask != null : "setDataDirectorySuffix must be called first.";
        return getDirectoryPath(DATA_DIRECTORY);
    }

    /**
     * @return the cache directory.
     */
    @CalledByNative
    public static String getCacheDirectory() {
        assert sDirPathFetchTask != null : "setDataDirectorySuffix must be called first.";
        return getDirectoryPath(CACHE_DIRECTORY);
    }

    @CalledByNative
    public static String getThumbnailCacheDirectory() {
        assert sDirPathFetchTask != null : "setDataDirectorySuffix must be called first.";
        return getDirectoryPath(THUMBNAIL_DIRECTORY);
    }

    /**
     * Returns the downloads directory. Before Android Q, this returns the public download directory
     * for Chrome app. On Q+, this returns the first private download directory for the app, since Q
     * will block public directory access. May return empty string when there are no external
     * storage volumes mounted.
     */
    @SuppressWarnings("unused")
    @CalledByNative
    public static @NonNull String getDownloadsDirectory() {
        // TODO(crbug.com/508615): Move calls to getDownloadsDirectory() to background thread.
        try (StrictModeContext ignored = StrictModeContext.allowDiskReads()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // https://developer.android.com/preview/privacy/scoped-storage
                // In Q+, Android has begun sandboxing external storage. Chrome may not have
                // permission to write to Environment.getExternalStoragePublicDirectory(). Instead
                // using Context.getExternalFilesDir() will return a path to sandboxed external
                // storage for which no additional permissions are required.
                String[] dirs = getAllPrivateDownloadsDirectories();
                assert dirs != null;
                return dirs.length == 0 ? "" : dirs[0];
            }
            return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    .getPath();
        }
    }

    /**
     * @return Download directories including the default storage directory on SD card, and a
     * private directory on external SD card.
     */
    @SuppressWarnings("unused")
    @CalledByNative
    public static @NonNull String[] getAllPrivateDownloadsDirectories() {
        List<File> files = new ArrayList<>();
        try (StrictModeContext ignored = StrictModeContext.allowDiskWrites()) {
            File[] externalDirs = ContextUtils.getApplicationContext().getExternalFilesDirs(
                    Environment.DIRECTORY_DOWNLOADS);
            files = (externalDirs == null) ? files : Arrays.asList(externalDirs);
        }
        return toAbsolutePathStrings(files);
    }

    /**
     * @return The download directory for secondary storage on Q+, returned by
     * {@link MediaStore#getExternalVolumeNames(Context)}. Notices on Android R, apps can no longer
     * expose app's private directory for secondary storage. Apps should put files to
     * /storage/$volume_id/Download/ directory instead.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    @CalledByNative
    public static @NonNull String[] getExternalDownloadVolumesNames() {
        ArrayList<File> files = new ArrayList<>();
        Set<String> volumes =
                ApiHelperForQ.getExternalVolumeNames(ContextUtils.getApplicationContext());
        for (String vol : volumes) {
            if (!TextUtils.isEmpty(vol) && !vol.contains(MediaStore.VOLUME_EXTERNAL_PRIMARY)) {
                StorageManager manager = ApiHelperForM.getSystemService(
                        ContextUtils.getApplicationContext(), StorageManager.class);
                File volumeDir =
                        ApiHelperForR.getVolumeDir(manager, MediaStore.Files.getContentUri(vol));
                File volumeDownloadDir = new File(volumeDir, Environment.DIRECTORY_DOWNLOADS);
                // Happens in rare case when Android doesn't create the download directory for this
                // volume.
                if (!volumeDownloadDir.isDirectory()) {
                    Log.w(TAG, "Download dir missing: %s, parent dir:%s, isDirectory:%s",
                            volumeDownloadDir.getAbsolutePath(), volumeDir.getAbsolutePath(),
                            volumeDir.isDirectory());
                }
                files.add(volumeDownloadDir);
            }
        }

        return toAbsolutePathStrings(files);
    }

    private static @NonNull String[] toAbsolutePathStrings(@NonNull List<File> files) {
        ArrayList<String> absolutePaths = new ArrayList<String>();
        for (File file : files) {
            if (file == null || TextUtils.isEmpty(file.getAbsolutePath())) continue;
            absolutePaths.add(file.getAbsolutePath());
        }

        return absolutePaths.toArray(new String[absolutePaths.size()]);
    }

    /**
     * @return the path to native libraries.
     */
    @SuppressWarnings("unused")
    @CalledByNative
    private static String getNativeLibraryDirectory() {
        ApplicationInfo ai = ContextUtils.getApplicationContext().getApplicationInfo();
        if ((ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                || (ai.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
            return ai.nativeLibraryDir;
        }

        return "/system/lib/";
    }

    /**
     * @return the external storage directory.
     */
    @SuppressWarnings("unused")
    @CalledByNative
    public static String getExternalStorageDirectory() {
        return Environment.getExternalStorageDirectory().getAbsolutePath();
    }
}
