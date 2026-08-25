package com.lladlam.melox.ui.account

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lladlam.melox.ui.glass.MeloXSymbol
import com.lladlam.melox.ui.glass.MeloXSymbolIcon
import com.lladlam.melox.ui.legal.MeloXLegalLinks
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal const val PHONE_CODE_RESEND_SECONDS = 60

internal fun normalizePhoneInput(value: String): String = value.filter { it.isDigit() || it == ' ' || it == '-' }.take(24)
internal fun normalizedPhone(value: String): String = value.filter(Char::isDigit)
internal fun normalizeVerificationCode(value: String): String = value.filter(Char::isDigit).take(8)
internal fun isValidPhone(value: String): Boolean = normalizedPhone(value).length in 5..15
internal fun isValidVerificationCode(value: String): Boolean = normalizeVerificationCode(value).length in 4..8

private enum class PhoneLoginStep { Phone, Code }

@Composable
fun MeloXPhoneCodeLoginScreen(
    serviceName: String,
    brandColor: Color,
    description: String,
    onClose: () -> Unit,
    onSendCode: suspend (countryCode: String, phone: String) -> Result<Unit>,
    onSubmitCode: suspend (countryCode: String, phone: String, code: String) -> Result<Unit>,
    onWebLogin: () -> Unit,
    webFallbackEmphasis: Boolean = false,
    initialPhone: String = "",
    startAtCodeStep: Boolean = false,
    onPhoneChanged: (String) -> Unit = {},
) {
    var step by remember(startAtCodeStep) {
        mutableStateOf(if (startAtCodeStep) PhoneLoginStep.Code else PhoneLoginStep.Phone)
    }
    val countryCode = "86"
    var phone by remember(initialPhone) { mutableStateOf(normalizePhoneInput(initialPhone)) }
    var code by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var resendSeconds by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val phoneFocusRequester = remember { FocusRequester() }
    val codeFocusRequester = remember { FocusRequester() }

    fun send() {
        if (loading) return
        if (!isValidPhone(phone)) {
            error = "请输入有效手机号"
            return
        }
        loading = true
        error = null
        scope.launch {
            onSendCode(countryCode, normalizedPhone(phone))
                .onSuccess {
                    step = PhoneLoginStep.Code
                    resendSeconds = PHONE_CODE_RESEND_SECONDS
                }
                .onFailure { error = it.message ?: "验证码发送失败，请稍后重试" }
            loading = false
        }
    }

    fun submit() {
        if (loading) return
        if (!isValidVerificationCode(code)) {
            error = "请输入有效验证码"
            return
        }
        loading = true
        error = null
        focusManager.clearFocus()
        scope.launch {
            onSubmitCode(countryCode, normalizedPhone(phone), normalizeVerificationCode(code))
                .onFailure { error = it.message ?: "验证码登录失败，请稍后重试" }
            loading = false
        }
    }

    BackHandler {
        if (step == PhoneLoginStep.Code) {
            step = PhoneLoginStep.Phone
            code = ""
            error = null
        } else onClose()
    }
    LaunchedEffect(step) {
        delay(120)
        if (step == PhoneLoginStep.Phone) phoneFocusRequester.requestFocus()
        else codeFocusRequester.requestFocus()
    }
    LaunchedEffect(resendSeconds) {
        if (resendSeconds > 0) {
            delay(1_000)
            resendSeconds -= 1
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .imePadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 54.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(brandColor.copy(alpha = 0.74f), brandColor),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    MeloXSymbolIcon(
                        symbol = MeloXSymbol.MusicNote,
                        modifier = Modifier.size(36.dp),
                        color = Color.White,
                        contentDescription = "$serviceName 音乐服务",
                        iconSize = 32.sp,
                    )
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    text = "登录 $serviceName",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 28.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = if (step == PhoneLoginStep.Phone) {
                        description
                    } else {
                        "输入发送至 +$countryCode ${phone.trim()} 的验证码。"
                    },
                    modifier = Modifier
                        .widthIn(max = 480.dp)
                        .padding(top = 10.dp, bottom = 24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 17.sp,
                    lineHeight = 23.sp,
                    textAlign = TextAlign.Center,
                )

                if (step == PhoneLoginStep.Phone) {
                    ApplePhoneField(
                        value = phone,
                        countryCode = countryCode,
                        brandColor = brandColor,
                        enabled = !loading,
                        isError = error != null,
                        focusRequester = phoneFocusRequester,
                        onValueChange = {
                            phone = normalizePhoneInput(it)
                            onPhoneChanged(phone)
                            error = null
                        },
                        onDone = { send() },
                    )
                } else {
                    AppleVerificationFields(
                        phone = "+$countryCode ${phone.trim()}",
                        code = code,
                        brandColor = brandColor,
                        enabled = !loading,
                        isError = error != null,
                        loading = loading,
                        focusRequester = codeFocusRequester,
                        onCodeChange = {
                            code = normalizeVerificationCode(it)
                            error = null
                        },
                        onSubmit = { submit() },
                    )
                }

                error?.let {
                    Text(
                        text = it,
                        modifier = Modifier
                            .widthIn(max = 480.dp)
                            .fillMaxWidth()
                            .padding(top = 10.dp, start = 2.dp, end = 2.dp),
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    )
                }
                if (step == PhoneLoginStep.Code) {
                    TextButton(
                        onClick = { send() },
                        enabled = resendSeconds == 0 && !loading,
                        modifier = Modifier.padding(top = 6.dp),
                    ) {
                        Text(
                            if (resendSeconds > 0) "${resendSeconds} 秒后可重新发送" else "重新发送验证码",
                            color = if (resendSeconds == 0) brandColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
            }

            Row(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.Top,
            ) {
                MeloXSymbolIcon(
                    symbol = MeloXSymbol.Info,
                    modifier = Modifier
                        .padding(top = 1.dp)
                        .size(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    contentDescription = null,
                    iconSize = 15.sp,
                )
                Text(
                    text = "你的手机号和验证码只会发送给 $serviceName，用于完成登录；验证码不会保存在 MeloX 中。",
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }

            if (step == PhoneLoginStep.Phone) {
                Box(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .widthIn(max = 320.dp)
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isValidPhone(phone) && !loading) {
                                brandColor
                            } else {
                                brandColor.copy(alpha = 0.38f)
                            },
                        )
                        .clickable(
                            enabled = isValidPhone(phone) && !loading,
                            role = Role.Button,
                            onClick = { send() },
                        )
                        .semantics { contentDescription = "继续登录 $serviceName" },
                    contentAlignment = Alignment.Center,
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(
                            text = "继续",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
            TextButton(
                onClick = onWebLogin,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { role = Role.Button },
            ) {
                Text(
                    text = if (step == PhoneLoginStep.Code) "收不到验证码？使用网页登录" else "使用网页登录",
                    color = if (webFallbackEmphasis) brandColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    fontWeight = if (webFallbackEmphasis) FontWeight.Medium else FontWeight.Normal,
                )
            }
            MeloXLegalLinks(
                modifier = Modifier.padding(bottom = 2.dp),
                tint = brandColor,
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(role = Role.Button, onClick = onClose)
                .semantics { contentDescription = "关闭手机号登录" },
            contentAlignment = Alignment.Center,
        ) {
            MeloXSymbolIcon(
                symbol = MeloXSymbol.Xmark,
                modifier = Modifier.size(14.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                contentDescription = null,
                iconSize = 13.sp,
            )
        }
    }
}

@Composable
private fun ApplePhoneField(
    value: String,
    countryCode: String,
    brandColor: Color,
    enabled: Boolean,
    isError: Boolean,
    focusRequester: FocusRequester,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val floatingLabel = focused || value.isNotEmpty()
    val shape = RoundedCornerShape(12.dp)
    val borderColor = when {
        isError -> MaterialTheme.colorScheme.error
        focused -> brandColor
        else -> MaterialTheme.colorScheme.outline
    }
    val backgroundColor = if (isError && !focused) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.32f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .widthIn(max = 480.dp)
            .fillMaxWidth()
            .height(56.dp)
            .clip(shape)
            .background(backgroundColor)
            .border(if (focused) 2.dp else 1.dp, borderColor, shape)
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused },
        enabled = enabled,
        textStyle = TextStyle(
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 17.sp,
            lineHeight = 22.sp,
        ),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Phone,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        singleLine = true,
        cursorBrush = SolidColor(brandColor),
        decorationBox = { innerTextField ->
            Box(modifier = Modifier.fillMaxSize()) {
                if (floatingLabel) {
                    Text(
                        text = "手机号",
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 16.dp, top = 7.dp),
                        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 15.sp,
                    )
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = if (floatingLabel) 13.dp else 0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "+$countryCode",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 17.sp,
                    )
                    Spacer(Modifier.size(8.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (!floatingLabel && value.isEmpty()) {
                            Text(
                                text = "手机号",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 17.sp,
                            )
                        }
                        innerTextField()
                    }
                }
            }
        },
    )
}

@Composable
private fun AppleVerificationFields(
    phone: String,
    code: String,
    brandColor: Color,
    enabled: Boolean,
    isError: Boolean,
    loading: Boolean,
    focusRequester: FocusRequester,
    onCodeChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val floatingLabel = focused || code.isNotEmpty()
    val shape = RoundedCornerShape(12.dp)
    val borderColor = when {
        isError -> MaterialTheme.colorScheme.error
        focused -> brandColor
        else -> MaterialTheme.colorScheme.outline
    }
    val backgroundColor = if (isError && !focused) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.32f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val canSubmit = isValidVerificationCode(code) && !loading

    Column(
        modifier = Modifier
            .widthIn(max = 480.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(backgroundColor)
            .border(if (focused) 2.dp else 1.dp, borderColor, shape),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "手机号",
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 15.sp,
            )
            Text(
                text = phone,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 17.sp,
                lineHeight = 22.sp,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (focused) 0.dp else 16.dp)
                .height(1.dp)
                .background(if (focused) brandColor else MaterialTheme.colorScheme.outlineVariant),
        )
        BasicTextField(
            value = code,
            onValueChange = onCodeChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .focusRequester(focusRequester)
                .onFocusChanged { focused = it.isFocused },
            enabled = enabled,
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 17.sp,
                lineHeight = 22.sp,
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            singleLine = true,
            cursorBrush = SolidColor(brandColor),
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.fillMaxSize()) {
                    if (floatingLabel) {
                        Text(
                            text = "验证码",
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(start = 16.dp, top = 7.dp),
                            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            lineHeight = 15.sp,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 58.dp, top = if (floatingLabel) 13.dp else 0.dp),
                    ) {
                        if (!floatingLabel && code.isEmpty()) {
                            Text(
                                text = "验证码",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 17.sp,
                            )
                        }
                        innerTextField()
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 10.dp)
                            .size(34.dp)
                            .clip(CircleShape)
                            .border(
                                width = 1.5.dp,
                                color = if (canSubmit) brandColor else MaterialTheme.colorScheme.outlineVariant,
                                shape = CircleShape,
                            )
                            .clickable(
                                enabled = canSubmit,
                                role = Role.Button,
                                onClick = onSubmit,
                            )
                            .semantics { contentDescription = "提交验证码" },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = brandColor,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            MeloXSymbolIcon(
                                symbol = MeloXSymbol.ChevronRight,
                                modifier = Modifier.size(17.dp),
                                color = if (canSubmit) brandColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                contentDescription = null,
                                iconSize = 16.sp,
                            )
                        }
                    }
                }
            },
        )
    }
}
