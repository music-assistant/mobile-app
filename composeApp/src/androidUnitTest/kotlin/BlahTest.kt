package blah

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BlahTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun blah() {
        composeTestRule.setContent {
            Text("Blah")
        }

        composeTestRule.onNodeWithText("Blah").assertIsDisplayed()
    }
}
