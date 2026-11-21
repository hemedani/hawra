package ir.hawraa.quran.ui.screens.auth

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.hawraa.quran.R
import ir.hawraa.quran.domain.auth.AuthRules
import ir.hawraa.quran.domain.quran.toPersianDigits
import ir.hawraa.quran.ui.appLocale
import ir.hawraa.quran.ui.components.GlassElevation
import ir.hawraa.quran.ui.components.GlassSegments
import ir.hawraa.quran.ui.components.GlassSurface
import ir.hawraa.quran.ui.components.PasswordReveal
import ir.hawraa.quran.ui.theme.LocalGlassColors
import ir.hawraa.quran.ui.theme.LocalHawraaGeometry
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The gate: sign in or register, and nothing else until it is answered.
 *
 * Why a gate at all, given that the app is offline-first: history that is written before anyone is
 * signed in belongs to nobody, so it either gets attributed to the wrong account later or has to be
 * thrown away. Asking once, on first launch, is the cheapest possible way to keep every session
 * the reader records tied to the reader who recorded it. Everything *after* the gate stays
 * offline-first — the session is cached, and the app opens without a connection.
 *
 * There is no back button and no skip: this screen is not a destination, it is the condition for
 * the rest of the app to exist. The dock is hidden while it shows, for the same reason.
 */
@Composable
fun AuthScreen(viewModel: AuthViewModel = hiltViewModel()) {
    val form by viewModel.form.collectAsStateWithLifecycle()

    AuthContent(
        form = form,
        onMode = viewModel::setMode,
        onEmail = viewModel::setEmail,
        onPassword = viewModel::setPassword,
        onConfirmPassword = viewModel::setConfirmPassword,
        onDisplayName = viewModel::setDisplayName,
        onSubmit = viewModel::submit,
    )
}

/**
 * Stateless body, so the gate can be rendered in tests and in both locales without Hilt.
 *
 * One screen with two verbs rather than two screens: the fields are the same, the difference is a
 * name field and one word on the button. Two screens would be two copies of the same validation
 * and two chances to fix a bug in only one of them.
 *
 * ## The layout, and why it is shaped this way
 *
 * The gate is the first thing anyone sees and the only screen with no dock, no back and no
 * content — so it carries the whole weight of the app's first impression. Three bands, top to
 * bottom: an ornament that says *this is a Qur'an app* without a photograph, the wordmark, and one
 * pane holding the form. The ornament is drawn ([AuthMedallion]) rather than shipped as an image
 * because it has to work in both themes at any density, and a PNG would be three assets and a
 * mismatch waiting to happen.
 *
 * The fields are hand-built ([GlassField]) rather than `OutlinedTextField`s. Material's outlined
 * field draws its own 1 dp box, its own 16 sp label and its own floating-label animation, none of
 * which belong to this app's glass language — and none of which can be restyled into it. A field
 * here is a hairline pane that warms to the accent on focus, with the label *above* it: labels that
 * live inside the box disappear the moment there is text, which is exactly when a reader checking
 * a form wants to know which field they are looking at.
 */
@Composable
internal fun AuthContent(
    form: AuthFormState,
    onMode: (AuthMode) -> Unit,
    onEmail: (String) -> Unit,
    onPassword: (String) -> Unit,
    onConfirmPassword: (String) -> Unit,
    onDisplayName: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val geometry = LocalHawraaGeometry.current
    val glass = LocalGlassColors.current
    val persian = LocalConfiguration.current.appLocale().language == "fa"
    val register = form.mode == AuthMode.REGISTER

    // Saved, not remembered: a rotation while typing a password must not silently reveal it.
    var revealPassword by rememberSaveable { mutableStateOf(false) }
    var revealConfirm by rememberSaveable { mutableStateOf(false) }

    val passwordFocus = remember { FocusRequester() }
    val confirmFocus = remember { FocusRequester() }
    val nameFocus = remember { FocusRequester() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = geometry.spacing.xl,
                    end = geometry.spacing.xl,
                    top = geometry.spacing.xxl,
                    bottom = geometry.spacing.xl,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AuthMedallion()

            Spacer(Modifier.height(geometry.spacing.lg))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = glass.text,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(geometry.spacing.xxs))
            Text(
                text = stringResource(R.string.home_subtitle),
                style = MaterialTheme.typography.labelMedium,
                color = glass.textTertiary,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(geometry.spacing.xl))
            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(geometry.radius.card),
                strong = true,
                elevation = GlassElevation.Panel,
            ) {
                Column(modifier = Modifier.padding(geometry.spacing.xl)) {
                    Text(
                        text = stringResource(
                            if (register) R.string.auth_title_register else R.string.auth_title_signin,
                        ),
                        style = MaterialTheme.typography.titleLarge,
                        color = glass.text,
                    )
                    Spacer(Modifier.height(geometry.spacing.xs))
                    Text(
                        text = stringResource(
                            if (register) {
                                R.string.auth_subtitle_register
                            } else {
                                R.string.auth_subtitle_signin
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = glass.textSecondary,
                    )

                    Spacer(Modifier.height(geometry.spacing.lg))
                    GlassSegments(
                        options = AuthMode.entries,
                        selected = form.mode,
                        onSelect = onMode,
                        label = { mode ->
                            stringResource(
                                when (mode) {
                                    AuthMode.SIGN_IN -> R.string.auth_mode_signin
                                    AuthMode.REGISTER -> R.string.auth_mode_register
                                },
                            )
                        },
                        testTagOf = { mode ->
                            when (mode) {
                                AuthMode.SIGN_IN -> "auth-mode-signin"
                                AuthMode.REGISTER -> "auth-mode-register"
                            }
                        },
                    )

                    Spacer(Modifier.height(geometry.spacing.lg))
                    GlassField(
                        value = form.email,
                        onValueChange = onEmail,
                        label = stringResource(R.string.auth_email),
                        placeholder = stringResource(R.string.auth_email_placeholder),
                        leading = Icons.Filled.Email,
                        enabled = !form.busy,
                        keyboardType = KeyboardType.Email,
                        // An address is Latin and left-to-right even in a Persian sentence. Left
                        // alone, the bidi algorithm would type `a@b.com` back to front in an RTL
                        // paragraph, which looks correct and is not.
                        textDirection = TextDirection.Ltr,
                        imeAction = ImeAction.Next,
                        onImeAction = { if (!form.busy) passwordFocus.requestFocus() },
                        testTag = "auth-email",
                    )

                    Spacer(Modifier.height(geometry.spacing.md))
                    GlassField(
                        value = form.password,
                        onValueChange = onPassword,
                        label = stringResource(R.string.auth_password),
                        leading = Icons.Filled.Lock,
                        enabled = !form.busy,
                        focusRequester = passwordFocus,
                        visualTransformation = if (revealPassword) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardType = KeyboardType.Password,
                        textDirection = TextDirection.Ltr,
                        imeAction = if (register) ImeAction.Next else ImeAction.Done,
                        onImeAction = {
                            if (register) {
                                if (!form.busy) confirmFocus.requestFocus()
                            } else {
                                onSubmit()
                            }
                        },
                        trailing = {
                            PasswordReveal(
                                revealed = revealPassword,
                                onToggle = { revealPassword = !revealPassword },
                                description = stringResource(
                                    if (revealPassword) {
                                        R.string.auth_hide_password
                                    } else {
                                        R.string.auth_show_password
                                    },
                                ),
                                tint = glass.textTertiary,
                            )
                        },
                        testTag = "auth-password",
                    )

                    if (register) {
                        Spacer(Modifier.height(geometry.spacing.md))
                        GlassField(
                            value = form.confirmPassword,
                            onValueChange = onConfirmPassword,
                            label = stringResource(R.string.auth_password_confirm),
                            leading = Icons.Filled.Lock,
                            enabled = !form.busy,
                            focusRequester = confirmFocus,
                            visualTransformation = if (revealConfirm) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            keyboardType = KeyboardType.Password,
                            textDirection = TextDirection.Ltr,
                            imeAction = ImeAction.Next,
                            onImeAction = { if (!form.busy) nameFocus.requestFocus() },
                            trailing = {
                                PasswordReveal(
                                    revealed = revealConfirm,
                                    onToggle = { revealConfirm = !revealConfirm },
                                    description = stringResource(
                                        if (revealConfirm) {
                                            R.string.auth_hide_password
                                        } else {
                                            R.string.auth_show_password
                                        },
                                    ),
                                    tint = glass.textTertiary,
                                )
                            },
                            testTag = "auth-password-confirm",
                        )

                        Spacer(Modifier.height(geometry.spacing.md))
                        GlassField(
                            value = form.displayName,
                            onValueChange = onDisplayName,
                            label = stringResource(R.string.auth_display_name),
                            placeholder = stringResource(R.string.auth_name_placeholder),
                            leading = Icons.Filled.Person,
                            enabled = !form.busy,
                            focusRequester = nameFocus,
                            imeAction = ImeAction.Done,
                            onImeAction = onSubmit,
                            testTag = "auth-name",
                        )
                    }

                    val error = form.error
                    if (error != null) {
                        Spacer(Modifier.height(geometry.spacing.md))
                        AuthErrorLine(text = errorText(error, persian))
                    }

                    Spacer(Modifier.height(geometry.spacing.xl))
                    PrimaryAction(
                        label = stringResource(
                            if (register) {
                                R.string.auth_submit_register
                            } else {
                                R.string.auth_submit_signin
                            },
                        ),
                        enabled = form.canSubmit,
                        busy = form.busy,
                        onClick = onSubmit,
                    )
                }
            }

            Spacer(Modifier.height(geometry.spacing.lg))
            Text(
                text = stringResource(R.string.auth_offline_note),
                style = MaterialTheme.typography.labelSmall,
                color = glass.textTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * The ornament above the wordmark: an eight-point khatam inside two rings.
 *
 * Eight points, not six or twelve: the khatam (خاتم) is the eight-pointed star of the geometric
 * tradition this app's chrome borrows from, and it is drawn as one closed polygon with alternating
 * radii rather than as two overlapping squares — same figure, one path, and the stroke joins
 * cleanly at every vertex instead of leaving the doubled corners two squares produce.
 *
 * The glow is a radial gradient from `accent` to transparent, which is what stops the shape from
 * floating on the canvas as a sticker: the medallion appears to be lit from behind.
 */
@Composable
private fun AuthMedallion(diameter: Dp = 84.dp) {
    val glass = LocalGlassColors.current

    Canvas(modifier = Modifier.size(diameter)) {
        val radius = size.minDimension / 2f
        val middle = Offset(size.width / 2f, size.height / 2f)

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(glass.accent.copy(alpha = 0.18f), Color.Transparent),
                center = middle,
                radius = radius,
            ),
            radius = radius,
            center = middle,
        )
        drawCircle(
            color = glass.accent.copy(alpha = 0.22f),
            radius = radius * 0.97f,
            center = middle,
            style = Stroke(width = radius * 0.014f),
        )
        drawCircle(
            color = glass.mark.copy(alpha = 0.45f),
            radius = radius * 0.76f,
            center = middle,
            style = Stroke(width = radius * 0.016f),
        )

        val outer = radius * 0.60f
        val inner = radius * 0.35f
        val star = Path()
        repeat(16) { index ->
            // Start at twelve o'clock so the star sits square in its circle rather than tilted.
            val angle = (PI / 8.0) * index - PI / 2.0
            val reach = if (index % 2 == 0) outer else inner
            val x = middle.x + (reach * cos(angle)).toFloat()
            val y = middle.y + (reach * sin(angle)).toFloat()
            if (index == 0) star.moveTo(x, y) else star.lineTo(x, y)
        }
        star.close()
        drawPath(
            path = star,
            color = glass.accent.copy(alpha = 0.85f),
            style = Stroke(width = radius * 0.022f),
        )
        drawCircle(color = glass.accent, radius = radius * 0.05f, center = middle)
    }
}

/**
 * One field: a label above, a hairline pane below, and the pane warms to the accent while focused.
 *
 * Built on `BasicTextField` rather than a Material field so the border, the padding and the label
 * position are ours. The label is outside the box on purpose — a floating label is unreadable at
 * exactly the moment it matters, when the box has text in it and the reader is checking which
 * field they just filled.
 *
 * The whole pane is the touch target (the `Row`), not just the glyph-and-cursor strip: tapping the
 * padding next to the text has to put the caret in the field, or the field feels broken.
 */
@Composable
private fun GlassField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    testTag: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    leading: ImageVector? = null,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardType: KeyboardType = KeyboardType.Text,
    textDirection: TextDirection = TextDirection.Content,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val glass = LocalGlassColors.current
    val geometry = LocalHawraaGeometry.current
    val shape = RoundedCornerShape(geometry.radius.md)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    val border by animateColorAsState(
        targetValue = if (focused) glass.accent else glass.glassBorder,
        label = "authFieldBorder",
    )

    Column(modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (focused) glass.accent else glass.textSecondary,
        )
        Spacer(Modifier.height(geometry.spacing.xs))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(glass.glassFill)
                .border(BorderStroke(1.dp, border), shape)
                .padding(
                    start = geometry.spacing.md,
                    end = if (trailing != null) geometry.spacing.xs else geometry.spacing.md,
                    top = geometry.spacing.sm,
                    bottom = geometry.spacing.sm,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                Icon(
                    imageVector = leading,
                    contentDescription = null,
                    tint = if (focused) glass.accent else glass.textTertiary,
                    modifier = Modifier.size(geometry.icons.md),
                )
                Spacer(Modifier.width(geometry.spacing.sm))
            }

            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = glass.text,
                    textDirection = textDirection,
                ),
                cursorBrush = SolidColor(glass.accent),
                visualTransformation = visualTransformation,
                keyboardOptions = KeyboardOptions(
                    keyboardType = keyboardType,
                    imeAction = imeAction,
                ),
                keyboardActions = KeyboardActions(
                    onNext = { onImeAction?.invoke() },
                    onDone = { onImeAction?.invoke() },
                ),
                interactionSource = interaction,
                modifier = Modifier
                    .weight(1f)
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                    .testTag(testTag),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    textDirection = textDirection,
                                ),
                                color = glass.textTertiary,
                                maxLines = 1,
                            )
                        }
                        innerTextField()
                    }
                },
            )

            if (trailing != null) {
                Spacer(Modifier.width(geometry.spacing.xs))
                trailing()
            }
        }
    }
}

/**
 * The failure line, as a tinted strip rather than bare red text.
 *
 * A red sentence under the last field is easy to miss between two glass panes, and this screen has
 * no snackbar to fall back on. The strip gives the message an edge of its own, and the tint is
 * derived from `danger` at low alpha so it works on both canvases without a second token.
 */
@Composable
private fun AuthErrorLine(text: String) {
    val glass = LocalGlassColors.current
    val geometry = LocalHawraaGeometry.current
    val shape = RoundedCornerShape(geometry.radius.sm)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(glass.danger.copy(alpha = if (glass.dark) 0.18f else 0.09f))
            .border(BorderStroke(1.dp, glass.danger.copy(alpha = 0.32f)), shape)
            .padding(horizontal = geometry.spacing.md, vertical = geometry.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(geometry.spacing.sm),
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            tint = glass.danger,
            modifier = Modifier.size(geometry.icons.sm),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = glass.danger,
            modifier = Modifier.testTag("auth-error"),
        )
    }
}

/**
 * The gate's primary action, in three distinct states.
 *
 * Filled when it can be pressed, soft while it is working, flat when it cannot. The editor's save
 * action stops at the soft state, which is right for a screen that is *editing* something — but on
 * this screen the button is the only way forward, and a soft mint pill next to a hard green
 * selected segment reads as disabled. The ladder is what makes "not yet" and "go" impossible to
 * confuse.
 *
 * Busy swaps the label for a spinner in place rather than growing the pane: a button that changes
 * height mid-press makes the whole form jump, and on this screen the form is the only thing there
 * is to look at.
 */
@Composable
private fun PrimaryAction(
    label: String,
    enabled: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
) {
    val geometry = LocalHawraaGeometry.current
    val glass = LocalGlassColors.current
    val shape = RoundedCornerShape(geometry.radius.pill)
    val active = enabled && !busy

    Box(
        modifier = Modifier
            .fillMaxWidth()
            // Lifted only when it can be pressed: a shadow under a dead button suggests it is
            // floating there waiting for a tap that will not happen.
            .then(
                if (active) {
                    Modifier.shadow(
                        elevation = 8.dp,
                        shape = shape,
                        clip = false,
                        ambientColor = glass.accent.copy(alpha = 0.45f),
                        spotColor = glass.accent.copy(alpha = 0.45f),
                    )
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .background(
                when {
                    busy -> glass.accentSoft
                    active -> glass.accent
                    else -> glass.glassFill
                },
            )
            .border(
                BorderStroke(1.dp, if (active || busy) glass.accent else glass.glassBorder),
                shape,
            )
            .clickable(enabled = active, onClick = onClick)
            .padding(vertical = geometry.spacing.md)
            .testTag("auth-submit"),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = glass.accent,
            )
        } else {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (active) glass.onAccent else glass.textTertiary,
            )
        }
    }
}

/**
 * Turns a reason into a sentence in the reader's language.
 *
 * The minimum length is formatted with the locale's own digits: the number is part of a sentence,
 * not a measurement, and a Persian sentence with a Latin 8 in it reads like a bug.
 */
@Composable
private fun errorText(error: AuthError, persian: Boolean): String = when (error) {
    AuthError.EMAIL_INVALID -> stringResource(R.string.auth_error_email_invalid)
    AuthError.CREDENTIALS -> stringResource(R.string.auth_error_credentials)
    AuthError.DUPLICATE -> stringResource(R.string.auth_error_duplicate)
    AuthError.NETWORK -> stringResource(R.string.auth_error_network)
    AuthError.GENERIC -> stringResource(R.string.auth_error_generic)
    AuthError.PASSWORDS_DO_NOT_MATCH -> stringResource(R.string.auth_error_password_mismatch)
    AuthError.PASSWORD_TOO_SHORT -> {
        val min = AuthRules.MIN_PASSWORD_LENGTH
        stringResource(
            R.string.auth_error_password_short,
            if (persian) min.toPersianDigits() else min.toString(),
        )
    }
}
