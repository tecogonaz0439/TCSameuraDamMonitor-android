// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.ui.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.DpOffset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import net.tecogonaz.tcsameuradammonitor.BuildConfig
import net.tecogonaz.tcsameuradammonitor.R
import net.tecogonaz.tcsameuradammonitor.domain.model.AppInfoTexts
import net.tecogonaz.tcsameuradammonitor.domain.model.OssInfo
import net.tecogonaz.tcsameuradammonitor.ui.common.ClickableMarkdownText
import net.tecogonaz.tcsameuradammonitor.ui.common.MarkdownBody
import net.tecogonaz.tcsameuradammonitor.ui.common.mouseDragScroll
import net.tecogonaz.tcsameuradammonitor.ui.main.AppChooserDialog
import net.tecogonaz.tcsameuradammonitor.ui.main.AppLinkInfo
import net.tecogonaz.tcsameuradammonitor.ui.main.LinkDropdownMenu
import net.tecogonaz.tcsameuradammonitor.util.LocaleUtils
import java.util.Locale

/**
 * アプリケーションの名称、バージョン情報、ライセンス、利用規約、およびソースコードリポジトリへのリンクなどを
 * 表示する「このアプリについて」画面のメインComposable関数です。
 *
 * バージョン項目を5回タップすることで、隠しデバッグ設定 [debugSettingsVisible] を有効化する機能を備えています。
 * リストは、タッチ、マウスホイール、およびマウス左ボタンドラッグによる滑らかなスクロールに対応しています。
 *
 * @param availableApps リンク連携可能な外部アプリの一覧。
 * @param debugSettingsVisible デバッグ項目がすでに有効化されているかどうかのフラグ。
 * @param onOpenUrl 標準ブラウザ等でリンクを開くコールバック。
 * @param onOpenUrlWith 指定アプリでリンクを開くコールバック。
 * @param onShareText 共有用テキストを外部に送信するコールバック。
 * @param onCopied クリップボードにテキストがコピーされたときに呼ばれるコールバック。
 * @param onQueryApps リンク連携可能な外部アプリのクエリを開始するコールバック。
 * @param onClearApps アプリクエリの結果をクリアするコールバック。
 * @param onBackClick 画面左上の戻るボタンがタップされたときの遷移コールバック。メイン画面へ戻ります。
 * @param onTermsOfUseClick 英語版の利用規約画面へ遷移するためのコールバック。
 * @param onTermsOfUseJaClick 日本語版の利用規約画面へ遷移するためのコールバック。
 * @param onLicenseClick Apache License 2.0 全文画面へ遷移するためのコールバック。
 * @param onPrivacyPolicyClick 英語版のプライバシーポリシー画面へ遷移するためのコールバック。
 * @param onPrivacyPolicyJaClick 日本語版のプライバシーポリシー画面へ遷移するためのコールバック.
 * @param onOssLicenseClick 使用しているオープンソースライセンス（OSS）一覧画面へ遷移するためのコールバック.
 * @param onToggleDebugSettingsVisibility デバッグ設定項目の表示/非表示を切り替えるコールバック（隠しタップ成功時に呼び出されます）.
 * @param onDebugSettingsEnabledMessage デバッグ設定が有効化された際にシステム側でスナックバー等を表示するための追加コールバック.
 * @param showTopAppBar 画面上部の TopAppBar を表示するかどうかのフラグ.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    availableApps: List<AppLinkInfo>,
    debugSettingsVisible: Boolean,
    onOpenUrl: (String) -> Unit,
    onOpenUrlWith: (String, AppLinkInfo) -> Unit,
    onShareText: (String) -> Unit,
    onCopied: (String) -> Unit,
    onQueryApps: (String) -> Unit,
    onClearApps: () -> Unit,
    onBackClick: () -> Unit,
    onTermsOfUseClick: () -> Unit,
    onTermsOfUseJaClick: () -> Unit,
    onLicenseClick: () -> Unit,
    onPrivacyPolicyClick: () -> Unit,
    onPrivacyPolicyJaClick: () -> Unit,
    onOssLicenseClick: () -> Unit,
    onToggleDebugSettingsVisibility: () -> Unit = {},
    onDebugSettingsEnabledMessage: ((String) -> Unit)? = null,
    showTopAppBar: Boolean = true
) {
    var codebergExpanded by remember { mutableStateOf(false) }
    var showCodebergAppChooserDialog by remember { mutableStateOf(false) }
    var githubExpanded by remember { mutableStateOf(false) }
    var showGithubAppChooserDialog by remember { mutableStateOf(false) }
    var versionClickCount by remember { mutableIntStateOf(0) }
    val versionClickResetKey = remember { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(versionClickResetKey.intValue) {
        if (versionClickCount > 0) {
            delay(1500L)
            versionClickCount = 0
        }
    }
    val debugEnabledMsg = stringResource(R.string.debug_settings_enabled)
    val isJaLocale = LocaleUtils.effectiveLocale(LocalContext.current).language == Locale.JAPANESE.language

    val codebergUrl = "https://codeberg.org/tecogonaz0439/TCSameuraDamMonitor-android/"
    val githubUrl = "https://github.com/tecogonaz0439/TCSameuraDamMonitor-android/"
    val versionName = BuildConfig.VERSION_NAME
    val versionCode = BuildConfig.VERSION_CODE

    if (showCodebergAppChooserDialog) {
        AppChooserDialog(
            availableApps = availableApps,
            url = codebergUrl,
            onQueryApps = onQueryApps,
            onDismiss = {
                onClearApps()
                showCodebergAppChooserDialog = false
            },
            onAppSelected = { appInfo ->
                showCodebergAppChooserDialog = false
                onOpenUrlWith(codebergUrl, appInfo)
            }
        )
    }

    if (showGithubAppChooserDialog) {
        AppChooserDialog(
            availableApps = availableApps,
            url = githubUrl,
            onQueryApps = onQueryApps,
            onDismiss = {
                onClearApps()
                showGithubAppChooserDialog = false
            },
            onAppSelected = { appInfo ->
                showGithubAppChooserDialog = false
                onOpenUrlWith(githubUrl, appInfo)
            }
        )
    }

    Scaffold(
        topBar = {
            if (showTopAppBar) {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_info_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = stringResource(R.string.desc_back)
                            )
                        }
                    }
                )
            }
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
                )
            )
        }
    ) { padding ->
        val listState = rememberLazyListState()
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .mouseDragScroll(listState, Orientation.Vertical)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val context = LocalContext.current
                    val iconDrawable = remember {
                        ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
                    }
                    Image(
                        painter = BitmapPainter(iconDrawable!!.toBitmap().asImageBitmap()),
                        contentDescription = null,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.activity_main_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }
            item { HorizontalDivider() }

            item {
                AboutTwoLineRow(
                    icon = Icons.Outlined.Info,
                    title = stringResource(R.string.app_info_version),
                    subtitle = stringResource(R.string.dialog_version_format, versionName, versionCode),
                    onClick = {
                        versionClickCount++
                        versionClickResetKey.intValue++
                        if (!debugSettingsVisible && versionClickCount >= 5) {
                            versionClickCount = 0
                            onToggleDebugSettingsVisibility()
                            if (onDebugSettingsEnabledMessage != null) {
                                onDebugSettingsEnabledMessage(debugEnabledMsg)
                            } else {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(debugEnabledMsg)
                                }
                            }
                        }
                    }
                )
            }
            item { HorizontalDivider() }

            item {
                AboutTwoLineRow(
                    icon = Icons.Outlined.Description,
                    title = stringResource(R.string.app_info_terms_of_use),
                    subtitle = "",
                    onClick = if (isJaLocale) onTermsOfUseJaClick else onTermsOfUseClick
                )
            }
            item { HorizontalDivider() }

            if (!isJaLocale) {
                item {
                    AboutTwoLineRow(
                        icon = Icons.Outlined.Description,
                        title = stringResource(R.string.app_info_terms_of_use_ja),
                        subtitle = "",
                        onClick = onTermsOfUseJaClick
                    )
                }
                item { HorizontalDivider() }
            }

            item {
                AboutTwoLineRow(
                    icon = Icons.Outlined.Policy,
                    title = stringResource(R.string.app_info_privacy_policy),
                    subtitle = "",
                    onClick = if (isJaLocale) onPrivacyPolicyJaClick else onPrivacyPolicyClick
                )
            }
            item { HorizontalDivider() }

            if (!isJaLocale) {
                item {
                    AboutTwoLineRow(
                        icon = Icons.Outlined.Policy,
                        title = stringResource(R.string.app_info_privacy_policy_ja),
                        subtitle = "",
                        onClick = onPrivacyPolicyJaClick
                    )
                }
                item { HorizontalDivider() }
            }

            item {
                AboutTwoLineRow(
                    icon = Icons.Outlined.Code,
                    title = stringResource(R.string.app_info_license),
                    subtitle = stringResource(R.string.app_info_license_subtitle),
                    onClick = onLicenseClick
                )
            }
            item { HorizontalDivider() }

            item {
                AboutTwoLineRow(
                    icon = Icons.Outlined.Code,
                    title = stringResource(R.string.app_info_oss_license),
                    subtitle = "",
                    onClick = onOssLicenseClick
                )
            }
            item { HorizontalDivider() }

            item {
                val codebergTitle = stringResource(R.string.app_info_codeberg)
                var codebergTapX by remember { mutableFloatStateOf(0f) }
                var codebergTapY by remember { mutableFloatStateOf(0f) }
                val density = LocalDensity.current
                Box(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val down = event.changes.firstOrNull { it.pressed }
                                    if (down != null) {
                                        codebergTapX = down.position.x
                                        codebergTapY = down.position.y
                                    }
                                }
                            }
                            .clickable(
                                onClick = { codebergExpanded = true },
                                role = Role.Button,
                                onClickLabel = stringResource(
                                    R.string.desc_open_link_menu,
                                    stringResource(R.string.app_info_codeberg)
                                )
                            )
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_codeberg),
                            contentDescription = null,
                            modifier = Modifier.padding(end = 16.dp)
                        )
                        Column {
                            Text(
                                text = codebergTitle,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = codebergUrl,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Box(modifier = Modifier.align(Alignment.TopStart)) {
                        DropdownMenu(
                            expanded = codebergExpanded,
                            onDismissRequest = { codebergExpanded = false },
                            offset = with(density) { DpOffset(codebergTapX.toDp(), codebergTapY.toDp()) }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_open_url)) },
                                onClick = {
                                    codebergExpanded = false
                                    onOpenUrl(codebergUrl)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_open_url_with)) },
                                onClick = {
                                    codebergExpanded = false
                                    showCodebergAppChooserDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_share_url)) },
                                onClick = {
                                    codebergExpanded = false
                                    onShareText(codebergUrl)
                                }
                            )
                        }
                    }
                }
            }
            item { HorizontalDivider() }

            item {
                val githubTitle = stringResource(R.string.app_info_github)
                var githubTapX by remember { mutableFloatStateOf(0f) }
                var githubTapY by remember { mutableFloatStateOf(0f) }
                val density = LocalDensity.current
                Box(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val down = event.changes.firstOrNull { it.pressed }
                                    if (down != null) {
                                        githubTapX = down.position.x
                                        githubTapY = down.position.y
                                    }
                                }
                            }
                            .clickable(
                                onClick = { githubExpanded = true },
                                role = Role.Button,
                                onClickLabel = stringResource(
                                    R.string.desc_open_link_menu,
                                    stringResource(R.string.app_info_github)
                                )
                            )
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_github),
                            contentDescription = null,
                            modifier = Modifier.padding(end = 16.dp)
                        )
                        Column {
                            Text(
                                text = githubTitle,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = githubUrl,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Box(modifier = Modifier.align(Alignment.TopStart)) {
                        DropdownMenu(
                            expanded = githubExpanded,
                            onDismissRequest = { githubExpanded = false },
                            offset = with(density) { DpOffset(githubTapX.toDp(), githubTapY.toDp()) }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_open_url)) },
                                onClick = {
                                    githubExpanded = false
                                    onOpenUrl(githubUrl)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_open_url_with)) },
                                onClick = {
                                    githubExpanded = false
                                    showGithubAppChooserDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_share_url)) },
                                onClick = {
                                    githubExpanded = false
                                    onShareText(githubUrl)
                                }
                            )
                        }
                    }
                }
            }
            item { HorizontalDivider() }
        }
    }
}

@Composable
private fun OssLicenseItem(
    ossInfo: OssInfo,
    availableApps: List<AppLinkInfo>,
    onOpenUrl: (String) -> Unit,
    onOpenUrlWith: (String, AppLinkInfo) -> Unit,
    onShareText: (String) -> Unit,
    onCopied: (String) -> Unit,
    onQueryApps: (String) -> Unit,
    onClearApps: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        LinkDropdownMenu(
            title = ossInfo.url,
            url = ossInfo.url,
            availableApps = availableApps,
            onOpenUrl = onOpenUrl,
            onOpenUrlWith = onOpenUrlWith,
            onShareText = onShareText,
            onCopied = onCopied,
            onQueryApps = onQueryApps,
            onClearApps = onClearApps,
            textStyle = MaterialTheme.typography.bodyMedium,
            textColor = MaterialTheme.colorScheme.primary,
            headerContent = {
                Text(
                    text = ossInfo.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        )
        Spacer(Modifier.height(4.dp))
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(text = ossInfo.copyright, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))
                Text(text = ossInfo.licenseText, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppInfoLicenseScreen(
    availableApps: List<AppLinkInfo>,
    onOpenUrl: (String) -> Unit,
    onOpenUrlWith: (String, AppLinkInfo) -> Unit,
    onShareText: (String) -> Unit,
    onCopied: (String) -> Unit,
    onQueryApps: (String) -> Unit,
    onClearApps: () -> Unit,
    onBackClick: () -> Unit
) {
    AboutDetailScaffold(
        title = stringResource(R.string.dialog_license_title),
        onBackClick = onBackClick
    ) {
        item {
            LicenseBody(
                text = AppInfoTexts.LICENSE,
                availableApps = availableApps,
                onOpenUrl = onOpenUrl,
                onOpenUrlWith = onOpenUrlWith,
                onShareText = onShareText,
                onCopied = onCopied,
                onQueryApps = onQueryApps,
                onClearApps = onClearApps
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppInfoPrivacyPolicyScreen(
    onOpenUrl: (String) -> Unit,
    onBackClick: () -> Unit
) {
    AboutMarkdownDetailScreen(
        title = stringResource(R.string.dialog_privacy_policy_title),
        text = AppInfoTexts.PRIVACY_POLICY,
        onOpenUrl = onOpenUrl,
        onBackClick = onBackClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppInfoPrivacyPolicyJaScreen(
    onOpenUrl: (String) -> Unit,
    onBackClick: () -> Unit
) {
    AboutMarkdownDetailScreen(
        title = stringResource(R.string.dialog_privacy_policy_ja_title),
        text = AppInfoTexts.PRIVACY_POLICY_JA,
        onOpenUrl = onOpenUrl,
        onBackClick = onBackClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppInfoTermsOfUseScreen(
    onOpenUrl: (String) -> Unit,
    onBackClick: () -> Unit
) {
    AboutMarkdownDetailScreen(
        title = stringResource(R.string.dialog_terms_of_use_title),
        text = AppInfoTexts.TERMS_OF_USE,
        onOpenUrl = onOpenUrl,
        onBackClick = onBackClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppInfoTermsOfUseJaScreen(
    onOpenUrl: (String) -> Unit,
    onBackClick: () -> Unit
) {
    AboutMarkdownDetailScreen(
        title = stringResource(R.string.dialog_terms_of_use_ja_title),
        text = AppInfoTexts.TERMS_OF_USE_JA,
        onOpenUrl = onOpenUrl,
        onBackClick = onBackClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppInfoOssLicenseScreen(
    availableApps: List<AppLinkInfo>,
    onOpenUrl: (String) -> Unit,
    onOpenUrlWith: (String, AppLinkInfo) -> Unit,
    onShareText: (String) -> Unit,
    onCopied: (String) -> Unit,
    onQueryApps: (String) -> Unit,
    onClearApps: () -> Unit,
    onBackClick: () -> Unit
) {
    AboutDetailScaffold(
        title = stringResource(R.string.dialog_oss_license_title),
        onBackClick = onBackClick
    ) {
        itemsIndexed(AppInfoTexts.OSS_LIST) { index, ossInfo ->
            OssLicenseItem(
                ossInfo = ossInfo,
                availableApps = availableApps,
                onOpenUrl = onOpenUrl,
                onOpenUrlWith = onOpenUrlWith,
                onShareText = onShareText,
                onCopied = onCopied,
                onQueryApps = onQueryApps,
                onClearApps = onClearApps
            )
            if (index < AppInfoTexts.OSS_LIST.size - 1) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutMarkdownDetailScreen(
    title: String,
    text: String,
    onOpenUrl: (String) -> Unit,
    onBackClick: () -> Unit
) {
    AboutDetailScaffold(title = title, onBackClick = onBackClick) {
        item {
            MarkdownBody(
                text = text,
                onOpenUrl = onOpenUrl
            )
        }
    }
}

@ExperimentalMaterial3Api
@Composable
private fun AboutDetailScaffold(
    title: String,
    onBackClick: () -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.desc_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        val listState = rememberLazyListState()
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .mouseDragScroll(listState, Orientation.Vertical),
            contentPadding = PaddingValues(16.dp),
            content = content
        )
    }
}

@Composable
private fun AboutTwoLineRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick, role = Role.Button) else Modifier)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.padding(end = 16.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                if (subtitle.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun LicenseBody(
    text: String,
    availableApps: List<AppLinkInfo>,
    onOpenUrl: (String) -> Unit,
    onOpenUrlWith: (String, AppLinkInfo) -> Unit,
    onShareText: (String) -> Unit,
    onCopied: (String) -> Unit,
    onQueryApps: (String) -> Unit,
    onClearApps: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            val urlRegex = Regex("https?://[\\w./?=&%#@!+\\-~]+")
            val parts = text.split(urlRegex)
            val urls = urlRegex.findAll(text).map { it.value }.toList()

            val annotated = buildAnnotatedString {
                parts.forEachIndexed { index, part ->
                    append(part)
                    if (index < urls.size) {
                        val url = urls[index]
                        withLink(LinkAnnotation.Clickable("URL") { onOpenUrl(url) }) {
                            withStyle(
                                SpanStyle(
                                    color = MaterialTheme.colorScheme.primary,
                                    textDecoration = TextDecoration.Underline
                                )
                            ) {
                                append(url)
                            }
                        }
                    }
                }
            }

            BasicText(
                text = annotated,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
