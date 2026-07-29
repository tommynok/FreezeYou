package cf.playhi.freezeyou.ui

import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.widget.TextView
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.app.FreezeYouBaseActivity
import cf.playhi.freezeyou.utils.ThemeUtils.processActionBar
import cf.playhi.freezeyou.utils.ThemeUtils.processSetTheme

private const val CODE_HIGHLIGHT = 0x22808080
private const val BULLET_HANGING_INDENT_DP = 18

// Renders the lightweight heading/bullet/inline-code markup used by scheduledTaskCommandsSyntaxContent as a Spannable.
private fun renderCommandsMarkup(raw: String, density: Float): SpannableStringBuilder {
    val builder = SpannableStringBuilder()
    val hangingIndentPx = (BULLET_HANGING_INDENT_DP * density).toInt()
    for (line in raw.split("\n")) {
        when {
            line.isBlank() -> {}
            line.startsWith("# ") -> {
                if (builder.isNotEmpty()) builder.append("\n\n")
                appendHeading(builder, line.removePrefix("# "), 1.3f)
            }
            line.startsWith("## ") -> {
                if (builder.isNotEmpty()) builder.append("\n")
                appendHeading(builder, line.removePrefix("## "), 1.1f)
            }
            line.startsWith("- ") -> {
                val start = builder.length
                builder.append("•  ")
                appendInline(builder, line.removePrefix("- "))
                builder.append("\n")
                // Hanging indent: without it, a wrapped bullet's second line falls back to the
                // left margin and the whole list reads as one undifferentiated block of text.
                builder.setSpan(
                    LeadingMarginSpan.Standard(0, hangingIndentPx),
                    start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                // Blank line between bullets: with lines packed edge to edge the list read as
                // one solid block even with the hanging indent in place.
                builder.append("\n")
            }
            else -> {
                appendInline(builder, line)
                builder.append("\n")
            }
        }
    }
    return builder
}

private fun appendHeading(builder: SpannableStringBuilder, text: String, relativeSize: Float) {
    val start = builder.length
    appendInline(builder, text)
    builder.setSpan(StyleSpan(Typeface.BOLD), start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    builder.setSpan(RelativeSizeSpan(relativeSize), start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    builder.append("\n")
}

private fun appendInline(builder: SpannableStringBuilder, text: String) {
    text.split("`").forEachIndexed { index, part ->
        if (index % 2 == 1) {
            val start = builder.length
            builder.append(part)
            builder.setSpan(TypefaceSpan("monospace"), start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            builder.setSpan(BackgroundColorSpan(CODE_HIGHLIGHT), start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        } else {
            builder.append(part)
        }
    }
}

class ScheduledTaskCommandsSyntaxActivity : FreezeYouBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        processSetTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.scheduled_task_commands_syntax_activity)
        processActionBar(supportActionBar)

        findViewById<TextView>(R.id.stcsa_content_textView).text =
            renderCommandsMarkup(
                getString(R.string.scheduledTaskCommandsSyntaxContent),
                resources.displayMetrics.density
            )
    }
}
