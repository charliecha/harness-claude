package com.harnessclaude.llm.threading

import android.os.Looper

/**
 * Indirection over [Looper] so unit tests can simulate "called on main"
 * without depending on Android's main looper.
 */
fun interface ThreadChecker {
    /** @return `true` if the *current* thread is the main UI thread. */
    fun isOnMainThread(): Boolean

    companion object {
        /** Production implementation; uses real Android Looper. */
        val Real: ThreadChecker =
            ThreadChecker {
                Looper.myLooper() == Looper.getMainLooper()
            }

        /** Test helper — always reports "on main". */
        val AlwaysOnMain: ThreadChecker = ThreadChecker { true }

        /** Test helper — always reports "off main". */
        val NotOnMainAlways: ThreadChecker = ThreadChecker { false }
    }
}
