package com.danzucker.stitchpad.feature.tutorials.presentation

import androidx.compose.runtime.Composable
import com.danzucker.stitchpad.feature.tutorials.domain.model.Tutorial
import com.danzucker.stitchpad.feature.tutorials.domain.model.TutorialTopic
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.tutorial_add_customer_desc
import stitchpad.composeapp.generated.resources.tutorial_add_customer_title
import stitchpad.composeapp.generated.resources.tutorial_create_order_desc
import stitchpad.composeapp.generated.resources.tutorial_create_order_title
import stitchpad.composeapp.generated.resources.tutorial_quick_start_desc
import stitchpad.composeapp.generated.resources.tutorial_quick_start_title
import stitchpad.composeapp.generated.resources.tutorial_reports_desc
import stitchpad.composeapp.generated.resources.tutorial_reports_title
import stitchpad.composeapp.generated.resources.tutorial_styles_desc
import stitchpad.composeapp.generated.resources.tutorial_styles_title

/**
 * Resolves the localized title/description for a [Tutorial]. Known [TutorialTopic]s use string
 * resources (so the 5 contextual clips are translatable and never hold copy in the data layer);
 * library-only clips with no enum topic fall back to their remote-authored Firestore text.
 */
@Composable
fun tutorialTitle(tutorial: Tutorial): String = when (tutorial.topic) {
    TutorialTopic.QuickStart -> stringResource(Res.string.tutorial_quick_start_title)
    TutorialTopic.AddCustomer -> stringResource(Res.string.tutorial_add_customer_title)
    TutorialTopic.CreateOrder -> stringResource(Res.string.tutorial_create_order_title)
    TutorialTopic.Styles -> stringResource(Res.string.tutorial_styles_title)
    TutorialTopic.Reports -> stringResource(Res.string.tutorial_reports_title)
    null -> tutorial.title
}

@Composable
fun tutorialDescription(tutorial: Tutorial): String = when (tutorial.topic) {
    TutorialTopic.QuickStart -> stringResource(Res.string.tutorial_quick_start_desc)
    TutorialTopic.AddCustomer -> stringResource(Res.string.tutorial_add_customer_desc)
    TutorialTopic.CreateOrder -> stringResource(Res.string.tutorial_create_order_desc)
    TutorialTopic.Styles -> stringResource(Res.string.tutorial_styles_desc)
    TutorialTopic.Reports -> stringResource(Res.string.tutorial_reports_desc)
    null -> tutorial.description
}
