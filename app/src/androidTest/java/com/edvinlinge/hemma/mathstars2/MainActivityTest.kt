package com.edvinlinge.hemma.mathstars2

import android.view.View
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @Test
    fun hubScrollViewReservesSpaceForVersionLabel() {
        val latch = CountDownLatch(1)
        var paddingBottom = 0
        var requiredPadding = 0

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val scrollView = activity.findViewById<NestedScrollView>(R.id.hubScrollView)
                val versionText = activity.findViewById<TextView>(R.id.versionText)

                scrollView.post {
                    val versionMargin = (versionText.layoutParams as android.view.ViewGroup.MarginLayoutParams)
                        .bottomMargin
                    val extraGap = activity.resources.getDimensionPixelSize(R.dimen.hub_version_clearance)
                    requiredPadding = versionText.height + versionMargin + extraGap
                    paddingBottom = scrollView.paddingBottom
                    latch.countDown()
                }
            }
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertTrue("Expected scroll padding ($paddingBottom) >= version clearance ($requiredPadding)", paddingBottom >= requiredPadding)
    }

    @Test
    fun lastCardDoesNotOverlapVersionWhenScrolledToEnd() {
        val latch = CountDownLatch(1)
        var cardBottom = 0
        var versionTop = 0

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val scrollView = activity.findViewById<NestedScrollView>(R.id.hubScrollView)
                val cardJulia = activity.findViewById<View>(R.id.cardJulia)
                val versionText = activity.findViewById<TextView>(R.id.versionText)

                scrollView.post {
                    scrollView.fullScroll(View.FOCUS_DOWN)
                    scrollView.post {
                        val cardLocation = IntArray(2)
                        cardJulia.getLocationOnScreen(cardLocation)
                        cardBottom = cardLocation[1] + cardJulia.height

                        val versionLocation = IntArray(2)
                        versionText.getLocationOnScreen(versionLocation)
                        versionTop = versionLocation[1]
                        latch.countDown()
                    }
                }
            }
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertTrue("Last card bottom ($cardBottom) should be above version top ($versionTop)", cardBottom <= versionTop)
    }
}
