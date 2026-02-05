@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.fibelatti.pinboard.features.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.fibelatti.pinboard.BuildConfig
import com.fibelatti.pinboard.R
import com.fibelatti.pinboard.core.AppMode
import com.fibelatti.pinboard.features.appstate.Action
import com.fibelatti.pinboard.features.appstate.All
import com.fibelatti.pinboard.features.appstate.Private
import com.fibelatti.pinboard.features.appstate.Public
import com.fibelatti.pinboard.features.appstate.Recent
import com.fibelatti.pinboard.features.appstate.Untagged
import com.fibelatti.pinboard.features.appstate.ViewAccountSwitcher
import com.fibelatti.pinboard.features.appstate.ViewNotes
import com.fibelatti.pinboard.features.appstate.ViewPopular
import com.fibelatti.pinboard.features.appstate.ViewPreferences
import com.fibelatti.pinboard.features.appstate.ViewRelays
import com.fibelatti.pinboard.features.appstate.ViewSavedFilters
import com.fibelatti.pinboard.features.appstate.ViewTags
import com.fibelatti.pinboard.features.nostr.vault.VaultState
import com.fibelatti.ui.components.AutoSizeText
import com.fibelatti.ui.preview.ThemePreviews
import com.fibelatti.ui.theme.ExtendedTheme

@Composable
fun NavigationMenuContent(
    appMode: AppMode,
    vaultState: VaultState,
    onNavOptionClicked: (Action) -> Unit,
    onLockVaultClicked: () -> Unit,
    onShareClicked: () -> Unit,
    onPrivacyPolicyClicked: () -> Unit,
    onLicensesClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationMenuContent(
        appMode = appMode,
        vaultState = vaultState,
        onAllClicked = { onNavOptionClicked(All) },
        onRecentClicked = { onNavOptionClicked(Recent) },
        onPublicClicked = { onNavOptionClicked(Public) },
        onPrivateClicked = { onNavOptionClicked(Private) },
        onUntaggedClicked = { onNavOptionClicked(Untagged) },
        onSavedFiltersClicked = { onNavOptionClicked(ViewSavedFilters) },
        onTagsClicked = { onNavOptionClicked(ViewTags) },
        onNotesClicked = { onNavOptionClicked(ViewNotes) },
        onPopularClicked = { onNavOptionClicked(ViewPopular) },
        onPreferencesClicked = { onNavOptionClicked(ViewPreferences) },
        onRelaysClicked = { onNavOptionClicked(ViewRelays) },
        onAccountsClicked = { onNavOptionClicked(ViewAccountSwitcher) },
        onLockVaultClicked = onLockVaultClicked,
        onShareClicked = onShareClicked,
        onPrivacyPolicyClicked = onPrivacyPolicyClicked,
        onLicensesClicked = onLicensesClicked,
        modifier = modifier,
    )
}

@Composable
private fun NavigationMenuContent(
    appMode: AppMode,
    vaultState: VaultState,
    onAllClicked: () -> Unit,
    onRecentClicked: () -> Unit,
    onPublicClicked: () -> Unit,
    onPrivateClicked: () -> Unit,
    onUntaggedClicked: () -> Unit,
    onSavedFiltersClicked: () -> Unit,
    onTagsClicked: () -> Unit,
    onNotesClicked: () -> Unit,
    onPopularClicked: () -> Unit,
    onPreferencesClicked: () -> Unit,
    onRelaysClicked: () -> Unit,
    onAccountsClicked: () -> Unit,
    onLockVaultClicked: () -> Unit,
    onShareClicked: () -> Unit,
    onPrivacyPolicyClicked: () -> Unit,
    onLicensesClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .nestedScroll(rememberNestedScrollInteropConnection())
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, top = 32.dp, end = 16.dp, bottom = 64.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        val serviceName = remember(appMode) {
            when (appMode) {
                AppMode.NOSTR -> R.string.nostr
                else -> null
            }
        }

        if (serviceName != null) {
            Text(
                text = stringResource(id = serviceName),
                color = MaterialTheme.colorScheme.primary,
                fontFamily = FontFamily.Serif,
                style = MaterialTheme.typography.headlineLarge,
            )

            // Vault status indicator for Nostr
            if (appMode == AppMode.NOSTR && vaultState != VaultState.NO_VAULT) {
                Spacer(modifier = Modifier.height(8.dp))
                VaultStatusIndicator(
                    vaultState = vaultState,
                    onLockClicked = onLockVaultClicked,
                )
            }

            Spacer(modifier = Modifier.height(30.dp))
        }

        MenuItem(
            textRes = R.string.menu_navigation_all,
            onClick = onAllClicked,
            iconRes = R.drawable.ic_bookmarks,
            shape = MaterialTheme.shapes.medium.copy(
                bottomStart = CornerSize(2.dp),
                bottomEnd = CornerSize(2.dp),
            ),
        )

        MenuItem(
            textRes = R.string.menu_navigation_recent,
            onClick = onRecentClicked,
            iconRes = R.drawable.ic_bookmarks,
        )

        // Show public/private filters for services that support it (including Nostr with vault)
        if (AppMode.NO_API != appMode) {
            MenuItem(
                textRes = R.string.menu_navigation_public,
                onClick = onPublicClicked,
                iconRes = R.drawable.ic_bookmarks,
            )

            MenuItem(
                textRes = R.string.menu_navigation_private,
                onClick = onPrivateClicked,
                iconRes = R.drawable.ic_bookmarks,
            )
        }

        MenuItem(
            textRes = R.string.menu_navigation_untagged,
            onClick = onUntaggedClicked,
            iconRes = R.drawable.ic_bookmarks,
            shape = MaterialTheme.shapes.medium.copy(
                topStart = CornerSize(2.dp),
                topEnd = CornerSize(2.dp),
            ),
        )

        Spacer(modifier = Modifier.height(30.dp))

        MenuItem(
            textRes = R.string.menu_navigation_saved_filters,
            onClick = onSavedFiltersClicked,
            iconRes = R.drawable.ic_filter,
            shape = MaterialTheme.shapes.medium.copy(
                bottomStart = CornerSize(2.dp),
                bottomEnd = CornerSize(2.dp),
            ),
        )

        MenuItem(
            textRes = R.string.menu_navigation_tags,
            onClick = onTagsClicked,
            iconRes = R.drawable.ic_tag,
            shape = MaterialTheme.shapes.medium.copy(
                topStart = CornerSize(2.dp),
                topEnd = CornerSize(2.dp),
            ),
        )

        Spacer(modifier = Modifier.height(30.dp))

        MenuItem(
            textRes = R.string.menu_navigation_preferences,
            onClick = onPreferencesClicked,
            iconRes = R.drawable.ic_preferences,
            shape = MaterialTheme.shapes.medium.copy(
                bottomStart = CornerSize(2.dp),
                bottomEnd = CornerSize(2.dp),
            ),
        )

        // Relays menu item (only for Nostr)
        if (appMode == AppMode.NOSTR) {
            MenuItem(
                textRes = R.string.menu_navigation_relays,
                onClick = onRelaysClicked,
                iconRes = R.drawable.ic_sync,
            )
        }

        MenuItem(
            textRes = R.string.menu_navigation_accounts,
            onClick = onAccountsClicked,
            iconRes = R.drawable.ic_person,
            shape = MaterialTheme.shapes.medium.copy(
                topStart = CornerSize(2.dp),
                topEnd = CornerSize(2.dp),
            ),
        )

        Spacer(modifier = Modifier.height(30.dp))

        MenuItem(
            textRes = R.string.about_share,
            onClick = onShareClicked,
            iconRes = R.drawable.ic_share,
            shape = MaterialTheme.shapes.medium.copy(
                bottomStart = CornerSize(2.dp),
                bottomEnd = CornerSize(2.dp),
            ),
        )

        MenuItem(
            textRes = R.string.about_privacy_policy,
            onClick = onPrivacyPolicyClicked,
            iconRes = R.drawable.ic_privacy_policy,
            shape = MaterialTheme.shapes.medium.copy(
                topStart = CornerSize(2.dp),
                topEnd = CornerSize(2.dp),
            ),
        )

        Spacer(modifier = Modifier.height(30.dp))

        AppVersionDetails(
            onClick = onLicensesClicked,
        )
    }
}

@Composable
private fun AppVersionDetails(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick, role = Role.Button)
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AutoSizeText(
            text = stringResource(id = R.string.about_developer),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            maxLines = 1,
        )

        Text(
            text = remember {
                val version = BuildConfig.VERSION_NAME
                val commit = BuildConfig.GIT_COMMIT
                if (commit.isNotEmpty()) "$version ($commit)" else version
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
        )

        Text(
            text = stringResource(id = R.string.about_based_on),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelSmall,
        )

        Text(
            text = stringResource(id = R.string.about_oss_licenses),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun VaultStatusIndicator(
    vaultState: VaultState,
    onLockClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(
                if (vaultState == VaultState.UNLOCKED) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                } else {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                },
            )
            .clickable(
                enabled = vaultState == VaultState.UNLOCKED,
                onClick = onLockClicked,
                role = Role.Button,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(
                id = if (vaultState == VaultState.UNLOCKED) {
                    R.drawable.ic_lock_open
                } else {
                    R.drawable.ic_lock
                },
            ),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (vaultState == VaultState.UNLOCKED) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(
                id = if (vaultState == VaultState.UNLOCKED) {
                    R.string.vault_status_unlocked
                } else {
                    R.string.vault_status_locked
                },
            ),
            color = if (vaultState == VaultState.UNLOCKED) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
            style = MaterialTheme.typography.labelMedium,
        )
        if (vaultState == VaultState.UNLOCKED) {
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = stringResource(id = R.string.vault_tap_to_lock),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun MenuItem(
    @StringRes textRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes iconRes: Int? = null,
    shape: CornerBasedShape = RoundedCornerShape(2.dp),
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize(),
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        if (iconRes != null) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = stringResource(id = textRes),
                modifier = Modifier
                    .size(32.dp)
                    .padding(end = 16.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }

        Text(
            text = stringResource(id = textRes),
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
@ThemePreviews
private fun NavigationMenuContentPreview() {
    ExtendedTheme {
        NavigationMenuContent(
            appMode = AppMode.NOSTR,
            vaultState = VaultState.UNLOCKED,
            onAllClicked = {},
            onRecentClicked = {},
            onPublicClicked = {},
            onPrivateClicked = {},
            onUntaggedClicked = {},
            onSavedFiltersClicked = {},
            onTagsClicked = {},
            onNotesClicked = {},
            onPopularClicked = {},
            onPreferencesClicked = {},
            onRelaysClicked = {},
            onAccountsClicked = {},
            onLockVaultClicked = {},
            onShareClicked = {},
            onPrivacyPolicyClicked = {},
            onLicensesClicked = {},
        )
    }
}
