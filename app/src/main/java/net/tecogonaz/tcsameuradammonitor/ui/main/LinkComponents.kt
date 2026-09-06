// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.ui.main

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.core.graphics.drawable.toBitmap
import net.tecogonaz.tcsameuradammonitor.R
import net.tecogonaz.tcsameuradammonitor.ui.common.mouseDragScroll


/**
 * リンク項目をタップした際に、コピー、共有、アプリで開く、などの
 * アクションメニュー（ドロップダウンメニュー）を表示するComposable関数です。
 *
 * タップした正確な座標（tapX, tapY）にドロップダウンメニューを表示します。
 *
 * @param title 表示するテキスト（タイトル）。
 * @param url 遷移先または処理対象のURL。
 * @param availableApps 連携可能な外部アプリのリスト。
 * @param onOpenUrl 標準ブラウザ等でURLを開くコールバック。
 * @param onOpenUrlWith 指定された特定のアプリでURLを開くコールバック。
 * @param onShareText テキストを共有するコールバック。
 * @param onCopied クリップボードへのコピーが成功したときに呼ばれるコールバック。
 * @param onQueryApps URLを開くことができるアプリのクエリを開始するコールバック。
 * @param onClearApps アプリクエリの結果をクリアするコールバック。
 * @param modifier このコンポーネントに適用する [Modifier]。
 * @param isGeo 地理座標（geo:）リンクであるかどうかのフラグ。メニュー文言が地図用に変わります。
 * @param horizontalPadding コンポーネントの左右パディング。
 * @param textToCopy コピー操作時にクリップボードに設定する文字列。省略された場合はURLがコピーされます。
 * @param textStyle タイトルのテキストスタイル。
 * @param textColor タイトルの描画色。
 * @param textAlign タイトルの配置位置。
 * @param headerContent タイトルの上に描画する追加のコンテンツ（Composable）。
 */
@Composable
fun LinkDropdownMenu(
    title: String,
    url: String,
    availableApps: List<AppLinkInfo>,
    onOpenUrl: (String) -> Unit,
    onOpenUrlWith: (String, AppLinkInfo) -> Unit,
    onShareText: (String) -> Unit,
    onCopied: (String) -> Unit,
    onQueryApps: (String) -> Unit,
    onClearApps: () -> Unit,
    modifier: Modifier = Modifier,
    isGeo: Boolean = false,
    horizontalPadding: androidx.compose.ui.unit.Dp = 8.dp,
    textToCopy: String? = null,
    textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
    textColor: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    headerContent: (@Composable () -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    var showAppChooserDialog by remember { mutableStateOf(false) }
    var tapX by remember { mutableFloatStateOf(0f) }
    var tapY by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val actualTextToCopy = textToCopy ?: url

    if (showAppChooserDialog) {
        AppChooserDialog(
            availableApps = availableApps,
            url = url,
            onQueryApps = onQueryApps,
            onDismiss = {
                onClearApps()
                showAppChooserDialog = false
            },
            onAppSelected = { appInfo ->
                showAppChooserDialog = false
                onOpenUrlWith(url, appInfo)
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val down = event.changes.firstOrNull { it.pressed }
                    if (down != null) {
                        tapX = down.position.x
                        tapY = down.position.y
                    }
                }
            }
            .clickable(
                onClick = { expanded = true },
                role = Role.Button,
                onClickLabel = stringResource(R.string.desc_open_link_menu, title)
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = 4.dp)) {
            if (headerContent != null) {
                headerContent()
            }
            Text(
                text = title,
                style = textStyle,
                color = textColor,
                textAlign = textAlign,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Box(modifier = Modifier.align(Alignment.TopStart)) {
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                offset = with(density) { DpOffset(tapX.toDp(), tapY.toDp()) }
            ) {
                        if (isGeo) {
                            
                            DropdownMenuItem(
                                text = { Text(stringResource(id = R.string.action_copy_geo_url)) },
                                onClick = {
                                    expanded = false
                                    clipboardManager.setText(AnnotatedString(actualTextToCopy))
                                    onCopied(actualTextToCopy)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(id = R.string.action_share_geo_url)) },
                                onClick = {
                                    expanded = false
                                    onShareText(actualTextToCopy)
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(id = R.string.action_open_map)) },
                                onClick = {
                                    expanded = false
                                    onOpenUrl(url)
                                }
                            )
                        } else {
                            
                            DropdownMenuItem(
                                text = { Text(stringResource(id = R.string.action_copy_link_title)) },
                                onClick = {
                                    expanded = false
                                    clipboardManager.setText(AnnotatedString(actualTextToCopy))
                                    onCopied(actualTextToCopy)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(id = R.string.action_share_link_title)) },
                                onClick = {
                                    expanded = false
                                    onShareText(actualTextToCopy)
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(id = R.string.action_open_url)) },
                                onClick = {
                                    expanded = false
                                    onOpenUrl(url)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(id = R.string.action_open_url_with)) },
                                onClick = {
                                    expanded = false
                                    showAppChooserDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(id = R.string.action_share_url)) },
                                onClick = {
                                    expanded = false
                                    shareText(context, url) { onShareText(it) }
                                }
                            )
                        }
                    }
                }
    }
}


/**
 * 指定されたURLを開くことができる、端末内の対応アプリ一覧を表示するダイアログです。
 *
 * @param availableApps 選択可能なアプリ（[AppLinkInfo]）のリスト。
 * @param url 対象のURL。
 * @param onQueryApps 対応するアプリ一覧のクエリを開始するコールバック。
 * @param onDismiss ダイアログを閉じる操作時のコールバック。
 * @param onAppSelected アプリが選択された時のコールバック。
 */
@Composable
fun AppChooserDialog(
    availableApps: List<AppLinkInfo>,
    url: String,
    onQueryApps: (String) -> Unit,
    onDismiss: () -> Unit,
    onAppSelected: (AppLinkInfo) -> Unit
) {
    androidx.compose.runtime.LaunchedEffect(url) {
        onQueryApps(url)
    }

    AlertDialog(
        onDismissRequest = {
            onDismiss()
        },
        title = { Text(stringResource(R.string.dialog_open_with_title)) },
        text = {
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .mouseDragScroll(listState, Orientation.Vertical)
                    .heightIn(max = 400.dp)
            ) {
                items(availableApps) { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAppSelected(app) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            bitmap = app.icon.toBitmap().asImageBitmap(),
                            contentDescription = null, 
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(app.name)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
            }) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}


/**
 * 指定されたURLを標準ブラウザなどの外部アプリで開くユーティリティ関数です。
 *
 * `http`, `https`, `geo` スキームのみを許容するセキュリティ保護が含まれています。
 *
 * @param context コンテキスト。
 * @param url 開く対象のURL。
 * @param onError エラー発生時にエラーメッセージを通知するコールバック。
 */
internal fun openUrl(context: Context, url: String, onError: (String) -> Unit) {
    try {
        if (!isAllowedExternalUrl(url, allowGeo = true)) {
            onError(context.getString(R.string.url_error_unsupported_scheme))
            return
        }
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        context.startActivity(intent)
    } catch (e: Exception) {
        onError(context.getString(R.string.url_error_cannot_open, e.message ?: ""))
    }
}


/**
 * 特定のパッケージ・アクティビティを指定してURLを開くユーティリティ関数です。
 *
 * `http`, `https` スキームのみを許容し、`geo` は許容しません。
 *
 * @param context コンテキスト。
 * @param url 開く対象のURL。
 * @param appInfo 起動対象の外部アプリ情報（[AppLinkInfo]）。
 * @param onError エラー発生時にエラーメッセージを通知するコールバック。
 */
internal fun openUrlWith(context: Context, url: String, appInfo: AppLinkInfo, onError: (String) -> Unit) {
    try {
        if (!isAllowedExternalUrl(url, allowGeo = false)) {
            onError(context.getString(R.string.url_error_unsupported_scheme))
            return
        }
        val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
            setClassName(appInfo.packageName, appInfo.activityName)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        onError(context.getString(R.string.url_error_cannot_open, e.message ?: ""))
    }
}

private fun isAllowedExternalUrl(url: String, allowGeo: Boolean): Boolean {
    val scheme = url.toUri().scheme?.lowercase() ?: return false
    return scheme == "http" || scheme == "https" || (allowGeo && scheme == "geo")
}


/**
 * 指定されたテキストを外部アプリに共有するための標準的なインテント（Intent.ACTION_SEND）を発行する関数です。
 *
 * @param context コンテキスト。
 * @param text 共有する文字列。
 * @param onError エラー発生時にエラーメッセージを通知するコールバック。
 */
internal fun shareText(context: Context, text: String, onError: (String) -> Unit) {
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(intent, null)
        context.startActivity(chooser)
    } catch (e: Exception) {
        onError(context.getString(R.string.action_share_error, e.message ?: ""))
    }
}


/**
 * ラベルとURLを組み合わせて表示する、設定画面や「このアプリについて」画面等で使用するリンク情報行です。
 *
 * タップするとコピーや外部アプリ連携用のドロップダウンメニューが表示されます。
 *
 * @param title 表示するリンクのタイトル（例: 国土交通省 水文水質データベース）。
 * @param url 対象のURL。
 * @param availableApps 連携可能なアプリのリスト。
 * @param onOpenUrl URLを開くコールバック。
 * @param onOpenUrlWith 指定アプリでURLを開くコールバック。
 * @param onShareText テキストを共有するコールバック。
 * @param onCopied クリップボードコピー完了時に呼ばれるコールバック。
 * @param onQueryApps アプリクエリを開始するコールバック。
 * @param onClearApps アプリクエリの結果をクリアするコールバック。
 * @param isGeo 地理座標リンクであるかどうかのフラグ。
 */
@Composable
fun UrlItemRow(
    title: String,
    url: String,
    availableApps: List<AppLinkInfo>,
    onOpenUrl: (String) -> Unit,
    onOpenUrlWith: (String, AppLinkInfo) -> Unit,
    onShareText: (String) -> Unit,
    onCopied: (String) -> Unit,
    onQueryApps: (String) -> Unit,
    onClearApps: () -> Unit,
    isGeo: Boolean = false
) {
    val textToCopy = if (isGeo) {
        url
    } else {
        title
    }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        LinkDropdownMenu(
            title = url,
            url = url,
            availableApps = availableApps,
            onOpenUrl = onOpenUrl,
            onOpenUrlWith = onOpenUrlWith,
            onShareText = onShareText,
            onCopied = onCopied,
            onQueryApps = onQueryApps,
            onClearApps = onClearApps,
            isGeo = isGeo,
            horizontalPadding = 0.dp,
            textToCopy = textToCopy,
            textStyle = MaterialTheme.typography.bodySmall,
            textColor = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.End,
            headerContent = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        )
    }
}


/**
 * ナビゲーションドロワー（NavigationDrawer）内で使用する、
 * タップすると遷移ではなくアクション（コピー・共有・開くなど）のメニューが開くドロワー項目です。
 *
 * 出典リンク（水文水質データベース、PDL1.0）などを他のドロワー項目と同一のUI表現で提供するために使用されます。
 *
 * @param title ドロワーに表示するラベル文字列。
 * @param icon ドロワーに表示するアイコン。
 * @param url 対象のURL。
 * @param availableApps 連携可能な外部アプリのリスト。
 * @param onOpenUrl URLを開くコールバック。
 * @param onOpenUrlWith 指定アプリでURLを開くコールバック。
 * @param onShareText テキストを共有するコールバック.
 * @param onCopied コピー完了時に呼ばれるコールバック.
 * @param onQueryApps アプリクエリを開始するコールバック.
 * @param onClearApps アプリクエリ結果をクリアするコールバック.
 * @param modifier このコンポーネントに適用する [Modifier].
 * @param textToCopy コピー時に使用する文字列.
 */
@Composable
fun LinkNavigationDrawerItem(
    title: String,
    icon: ImageVector,
    url: String,
    availableApps: List<AppLinkInfo>,
    onOpenUrl: (String) -> Unit,
    onOpenUrlWith: (String, AppLinkInfo) -> Unit,
    onShareText: (String) -> Unit,
    onCopied: (String) -> Unit,
    onQueryApps: (String) -> Unit,
    onClearApps: () -> Unit,
    modifier: Modifier = Modifier,
    textToCopy: String? = null
) {
    var expanded by remember { mutableStateOf(false) }
    var showAppChooserDialog by remember { mutableStateOf(false) }
    var tapX by remember { mutableFloatStateOf(0f) }
    var tapY by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val clipboardManager = LocalClipboardManager.current
    val actualTextToCopy = textToCopy ?: url

    if (showAppChooserDialog) {
        AppChooserDialog(
            availableApps = availableApps,
            url = url,
            onQueryApps = onQueryApps,
            onDismiss = {
                onClearApps()
                showAppChooserDialog = false
            },
            onAppSelected = { appInfo ->
                showAppChooserDialog = false
                onOpenUrlWith(url, appInfo)
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val down = event.changes.firstOrNull { it.pressed }
                    if (down != null) {
                        tapX = down.position.x
                        tapY = down.position.y
                    }
                }
            }
    ) {
        NavigationDrawerItem(
            icon = { Icon(icon, contentDescription = null) },
            label = { Text(title) },
            selected = false,
            onClick = { expanded = true },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )

        Box(modifier = Modifier.align(Alignment.TopStart)) {
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                offset = with(density) { DpOffset(tapX.toDp(), tapY.toDp()) }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(id = R.string.action_copy_link_title)) },
                    onClick = {
                        expanded = false
                        clipboardManager.setText(AnnotatedString(actualTextToCopy))
                        onCopied(actualTextToCopy)
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(id = R.string.action_share_link_title)) },
                    onClick = {
                        expanded = false
                        onShareText(actualTextToCopy)
                    }
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(id = R.string.action_open_url)) },
                    onClick = {
                        expanded = false
                        onOpenUrl(url)
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(id = R.string.action_open_url_with)) },
                    onClick = {
                        expanded = false
                        showAppChooserDialog = true
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(id = R.string.action_share_url)) },
                    onClick = {
                        expanded = false
                        onShareText(url)
                    }
                )
            }
        }
    }
}
